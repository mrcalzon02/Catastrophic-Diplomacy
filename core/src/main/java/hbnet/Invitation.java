package hbnet;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.io.*;
import java.util.*;

/** Private self-contained signed endpoint. Contains no password or friend graph.
 * This endpoint invitation confers no admission authority: room password/ACL still apply.
 */
public final class Invitation {
    private Invitation(){}
    public record Destination(String game,String host,int port,String fingerprint,long expires,String nonce){@Override public String toString(){return "Private Invitation";}}
    public static String create(String game,String host,int port,long expires,PrivateKey key,X509Certificate cert)throws GeneralSecurityException{
        if(!game.matches("[a-z][a-z0-9-]{1,47}")||expires<=System.currentTimeMillis()||expires-System.currentTimeMillis()>86400000L)throw new IllegalArgumentException("Invalid invitation lifetime");
        Hbnet.host(host);Hbnet.port(Integer.toString(port));Base64.Encoder b=Base64.getUrlEncoder().withoutPadding();
        String body="HBI1."+game+"."+b.encodeToString(host.getBytes(StandardCharsets.US_ASCII))+"."+port+"."+Hbnet.hash(cert.getEncoded())+"."+expires+"."+Hbnet.roomKey()+"."+b.encodeToString(cert.getEncoded());
        Signature sign=Signature.getInstance("SHA256withECDSA");sign.initSign(key);sign.update(body.getBytes(StandardCharsets.US_ASCII));return body+"."+b.encodeToString(sign.sign());
    }
    public static Destination parse(String token,String game)throws GeneralSecurityException,IOException{
        try{
            if(token==null||token.length()>4096||token.chars().anyMatch(c->c<33||c>126))throw Hbnet.failure();String[] p=token.split("\\.",-1);
            if(p.length!=9||!p[0].equals("HBI1")||!p[1].equals(game)||!p[6].matches("[0-9a-f]{40}"))throw Hbnet.failure();
            Base64.Decoder b=Base64.getUrlDecoder();X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(b.decode(p[7])));cert.checkValidity();
            if(!Hbnet.hash(cert.getEncoded()).equals(Hbnet.pin(p[4])))throw Hbnet.failure();
            Signature s=Signature.getInstance("SHA256withECDSA");s.initVerify(cert.getPublicKey());String body=String.join(".",Arrays.copyOf(p,8));s.update(body.getBytes(StandardCharsets.US_ASCII));if(!s.verify(b.decode(p[8])))throw Hbnet.failure();
            long expiry=Long.parseLong(p[5]);if(expiry<=System.currentTimeMillis()||expiry-System.currentTimeMillis()>86400000L)throw Hbnet.failure();
            return new Destination(game,Hbnet.host(new String(b.decode(p[2]),StandardCharsets.US_ASCII)),Hbnet.port(p[3]),p[4],expiry,p[6]);
        }catch(IllegalArgumentException e){throw Hbnet.failure();}
    }
}
