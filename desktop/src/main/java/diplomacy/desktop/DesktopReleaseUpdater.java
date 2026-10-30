package diplomacy.desktop;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import diplomacy.update.DesktopUpdatePolicy;
import diplomacy.update.UpdatePolicy;
import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Windows/Linux desktop check and opt-in download of ALL published release channels. */
final class DesktopReleaseUpdater {
    private static final String API="https://api.github.com/repos/"+UpdatePolicy.REPOSITORY+"/releases";
    private static final int MAX_PAGES=20, PAGE_SIZE=100, MAX_JSON=2*1024*1024;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final String ownTag;
    private final String assetName;

    DesktopReleaseUpdater(String ownTag,String osName) {
        this.ownTag=ownTag;
        this.assetName=DesktopUpdatePolicy.expectedAsset(osName);
    }

    void checkForUpdates() { checkForUpdates(true); }
    void checkForUpdates(boolean manual) {
        if(!busy.compareAndSet(false,true))return;
        Thread thread=new Thread(()->{
            try{
                Release latest=lookup();
                if(latest==null){
                    if(manual)message("No new release for this platform",
                            "You already have the newest published "+(assetName.endsWith(".msi")?"Windows":"Linux")+
                            " package on "+UpdatePolicy.REPOSITORY+".\nStable AND prereleases were checked.");
                    return;
                }
                confirmAndDownload(latest);
            }catch(Exception failure){
                if(manual)message("GitHub update check failed",
                        failure.getMessage()==null?"Could not check GitHub releases.":failure.getMessage());
                else System.err.println("CD desktop updater: "+failure.getMessage());
            }finally{busy.set(false);}
        },"CD-Desktop-Updater");
        thread.setDaemon(true);
        thread.start();
    }

    private Release lookup() throws Exception {
        if(ownTag==null||ownTag.isBlank())
            throw new IllegalStateException("This desktop build has no embedded release tag");
        JsonValue installed=json(API+"/tags/"+java.net.URLEncoder.encode(ownTag,java.nio.charset.StandardCharsets.UTF_8));
        if(installed.getBoolean("draft",false))
            throw new IllegalStateException("Installed release is not publicly published");
        String installedDate=installed.getString("published_at","");
        Instant.parse(installedDate);
        Release best=null;
        boolean complete=false;
        for(int page=1;page<=MAX_PAGES;page++){
            JsonValue releases=json(API+"?per_page="+PAGE_SIZE+"&page="+page);
            int count=0;
            for(JsonValue release=releases.child;release!=null;release=release.next){
                count++;
                if(!DesktopUpdatePolicy.eligibleRelease(release.getBoolean("draft",false),
                        release.getBoolean("prerelease",false)))continue;
                String tag=release.getString("tag_name","");
                if(tag.equals(ownTag))continue;
                String published=release.getString("published_at","");
                try{
                    if(!DesktopUpdatePolicy.isPublishedAfter(published,installedDate))continue;
                }catch(IllegalArgumentException invalid){continue;}
                JsonValue assets=release.get("assets");
                if(assets==null)continue;
                for(JsonValue item=assets.child;item!=null;item=item.next){
                    if(!assetName.equals(item.getString("name","")))continue;
                    long bytes=item.getLong("size",0L);
                    String url=item.getString("browser_download_url","");
                    try{DesktopUpdatePolicy.requireAsset(tag,assetName,url,bytes);}
                    catch(IllegalArgumentException malformed){continue;}
                    String digest=item.getString("digest","");
                    String checksum=digest.startsWith("sha256:")?digest.substring(7):"";
                    if(!checksum.isEmpty()&&!checksum.matches("[0-9a-fA-F]{64}"))continue;
                    if(best==null||Instant.parse(published).isAfter(Instant.parse(best.published))){
                        best=new Release(tag,published,url,bytes,checksum);
                    }
                }
            }
            if(count<PAGE_SIZE){complete=true;break;}
        }
        if(!complete)throw new IllegalStateException("GitHub release history exceeds scan limit");
        return best;
    }

    private void confirmAndDownload(Release release)throws Exception{
        final boolean[] accepted={false};
        SwingUtilities.invokeAndWait(()->{
            int choice=JOptionPane.showConfirmDialog(null,
                    "New Catastrophic Diplomacy release: "+release.tag+
                    "\nPackage: "+assetName+
                    "\nSize: "+(release.bytes/1048576)+" MiB"+
                    "\nPublished on GitHub (prereleases included).\n\nDownload this update?",
                    "Catastrophic Diplomacy — GitHub update",JOptionPane.YES_NO_OPTION);
            accepted[0]=choice==JOptionPane.YES_OPTION;
        });
        if(!accepted[0])return;
        Path directory=Path.of(System.getProperty("user.home"),"Downloads","Catastrophic-Diplomacy");
        Files.createDirectories(directory);
        // Always use a unique release-specific destination; never overwrite a previous installer.
        String safeTag=release.tag.replaceAll("[^A-Za-z0-9._-]","_");
        Path target=directory.resolve(safeTag+"-"+assetName);
        Path part=Files.createTempFile(directory,".cd-update-",".part");
        try{
            HttpURLConnection conn=connect(release.url);
            long received=0;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=conn.getInputStream();OutputStream out=Files.newOutputStream(part)){
                byte[] block=new byte[65536];int n;
                while((n=in.read(block))!=-1){
                    received+=n;
                    if(received>release.bytes||received>DesktopUpdatePolicy.MAX_DOWNLOAD_BYTES)
                        throw new IllegalStateException("Download exceeded published asset size");
                    digest.update(block,0,n);out.write(block,0,n);
                }
            }finally{conn.disconnect();}
            if(received!=release.bytes)throw new IllegalStateException("Downloaded file size mismatch");
            if(!release.sha256.isEmpty()&&!hex(digest.digest()).equalsIgnoreCase(release.sha256))
                throw new IllegalStateException("GitHub SHA-256 mismatch");
            if(assetName.endsWith(".msi")){
                try(InputStream in=Files.newInputStream(part)){
                    byte[] header=in.readNBytes(8);
                    if(!java.util.Arrays.equals(header,new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,
                            (byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1}))
                        throw new IllegalStateException("Downloaded Windows file is not an MSI");
                }
            }
            Files.move(part,target,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(part);}
        SwingUtilities.invokeAndWait(()->{
            try{
                int option=JOptionPane.showConfirmDialog(null,
                        "Downloaded "+target+"\n\n"+
                        (assetName.endsWith(".msi")?"Open the Windows installer now?":
                         "Open the download folder to extract the Linux package?"),
                        "Update ready",JOptionPane.YES_NO_OPTION);
                if(option==JOptionPane.YES_OPTION){
                    if(!Desktop.isDesktopSupported())throw new IllegalStateException("Desktop file opening unavailable");
                    Desktop.getDesktop().open((assetName.endsWith(".msi")?target:directory).toFile());
                }
            }catch(Exception failure){
                JOptionPane.showMessageDialog(null,"Update saved at:\n"+target+
                        "\nOpen it manually: "+failure.getMessage());
            }
        });
    }

    private static HttpURLConnection connect(String url)throws Exception{
        URI current=UpdatePolicy.allowedUrl(url);
        for(int i=0;i<6;i++){
            HttpURLConnection connection=(HttpURLConnection)current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent","CatastrophicDiplomacy-DesktopUpdater/1");
            connection.setRequestProperty("Accept","application/vnd.github+json,application/octet-stream");
            int status=connection.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=connection.getHeaderField("Location");
                connection.disconnect();
                if(location==null)throw new IllegalStateException("Missing GitHub download redirect");
                current=UpdatePolicy.allowedUrl(current.resolve(location).toString());continue;
            }
            if(status!=200){
                connection.disconnect();
                throw new IllegalStateException("GitHub returned HTTP "+status);
            }
            return connection;
        }
        throw new IllegalStateException("Too many GitHub redirects");
    }

    private static JsonValue json(String url)throws Exception{
        HttpURLConnection conn=connect(url);
        try(InputStream input=conn.getInputStream()){
            byte[] bytes=input.readNBytes(MAX_JSON+1);
            if(bytes.length>MAX_JSON)throw new IllegalStateException("GitHub release metadata too large");
            return new JsonReader().parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        }finally{conn.disconnect();}
    }
    private static String hex(byte[] bytes){
        StringBuilder result=new StringBuilder();
        for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private static void message(String title,String details){
        SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,details,title,JOptionPane.INFORMATION_MESSAGE));
    }
    private record Release(String tag,String published,String url,long bytes,String sha256){}
}
