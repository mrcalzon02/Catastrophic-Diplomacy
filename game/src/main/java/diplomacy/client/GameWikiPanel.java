package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.ArrayList;
import java.util.List;

/** Native, keyboard/touch accessible, paged searchable encyclopedia view. */
final class GameWikiPanel {
    private static final int ROWS=7;
    private static final Color BG=new Color(.018f,.026f,.043f,1f);
    private static final Color PANEL=new Color(.045f,.065f,.092f,1f);
    private static final Color EDGE=new Color(.15f,.25f,.32f,1f);
    private static final Color ACCENT=new Color(.24f,.72f,.86f,1f);
    private static final Color MUTED=new Color(.66f,.73f,.76f,1f);
    private final GameWikiCatalog catalog;
    private final boolean mobile;
    private List<GameWikiCatalog.Article> hits;
    private GameWikiCatalog.Article chosen;
    private int category,listPage,scroll;
    private String query="";
    private List<String> wrapped=List.of();
    GameWikiPanel(GameWikiCatalog catalog,boolean mobile){
        if(catalog==null)throw new IllegalArgumentException("Missing encyclopedia");
        this.catalog=catalog;this.mobile=mobile;
        category=1;refresh(); // Guides first: readable introduction, not 13k upgrades
    }
    String query(){return query;}
    int resultCount(){return hits.size();}
    String selectedId(){return chosen==null?null:chosen.id;}
    int categoryIndex(){return category;}
    int listPage(){return listPage;}
    int textScroll(){return scroll;}
    private void refresh(){
        String previous=chosen==null?null:chosen.id;
        hits=catalog.search(GameWikiCatalog.CATEGORIES[category],query);
        chosen=null;
        for(GameWikiCatalog.Article candidate:hits)
            if(candidate.id.equals(previous)){chosen=candidate;break;}
        if(chosen==null&&!hits.isEmpty())chosen=hits.get(0);
        listPage=chosen==null?0:Math.max(0,hits.indexOf(chosen)/ROWS);
        scroll=0;buildText();
    }
    void setQuery(String text){
        if(text==null)text="";
        StringBuilder clean=new StringBuilder();
        for(int i=0;i<text.length()&&clean.length()<72;i++){
            char c=text.charAt(i);
            if(c>=32&&c!=127)clean.append(c);
        }
        String next=clean.toString();
        if(!query.equals(next)){query=next;refresh();}
    }
    void type(char character){
        // BACKSPACE is handled by keyDown; keyTyped can also deliver ASCII 8.
        // Never erase twice for the same key press.
        if(character==8)return;
        if(character>=32&&character!=127&&query.length()<72)setQuery(query+character);
    }
    void erase(){
        if(!query.isEmpty())setQuery(query.substring(0,query.length()-1));
    }
    void setCategory(int index){
        if(index<0||index>=GameWikiCatalog.CATEGORIES.length)return;
        if(index!=category){category=index;refresh();}
    }
    void select(int index){
        if(index<0||index>=hits.size())return;
        chosen=hits.get(index);listPage=index/ROWS;scroll=0;buildText();
    }
    void pageList(int delta){
        listPage=Math.max(0,Math.min(Math.max(0,(hits.size()-1)/ROWS),listPage+delta));
    }
    void pageText(int delta){
        scroll=Math.max(0,Math.min(Math.max(0,wrapped.size()-visibleTextRows()),
                scroll+delta));
    }
    private int visibleTextRows(){return mobile?14:17;}
    private void buildText(){
        if(chosen==null){wrapped=List.of();return;}
        List<String> text=new ArrayList<>();
        for(String paragraph:chosen.lines){
            wrap(text,paragraph,mobile?62:85);
            text.add("");
        }
        wrapped=List.copyOf(text);
    }
    private static void wrap(List<String> output,String paragraph,int width){
        StringBuilder line=new StringBuilder();
        for(String word:paragraph.split(" ")){
            if(line.length()>0&&line.length()+word.length()+1>width){
                output.add(line.toString());line.setLength(0);
            }
            if(line.length()>0)line.append(' ');
            line.append(word);
        }
        if(line.length()>0)output.add(line.toString());
    }
    /** Return 1 for Android native search keyboard, 2 for close, 0 if handled. */
    int tap(float x,float y){
        if(GameMenuPanel.hit(x,y,1090,647,160,55))return 2;
        if(GameMenuPanel.hit(x,y,35,604,1040,42))return 1;
        if(GameMenuPanel.hit(x,y,1080,604,165,42)){setQuery("");return 0;}
        for(int i=0;i<GameWikiCatalog.CATEGORIES.length;i++){
            float cx=35+(i%6)*204,cy=555-(i/6)*43;
            if(GameMenuPanel.hit(x,y,cx,cy,198,38)){setCategory(i);return 0;}
        }
        for(int row=0;row<ROWS;row++){
            if(GameMenuPanel.hit(x,y,35,462-row*49,375,44)){
                select(listPage*ROWS+row);return 0;
            }
        }
        if(GameMenuPanel.hit(x,y,35,32,175,57)){pageList(-1);return 0;}
        if(GameMenuPanel.hit(x,y,235,32,175,57)){pageList(1);return 0;}
        if(GameMenuPanel.hit(x,y,455,32,175,57)){pageText(-visibleTextRows());return 0;}
        if(GameMenuPanel.hit(x,y,1065,32,175,57)){pageText(visibleTextRows());return 0;}
        return 0;
    }
    private static void box(ShapeRenderer s,float x,float y,float w,float h,Color c){
        s.setColor(c);s.rect(x,y,w,h);
    }
    private static void label(SpriteBatch batch,BitmapFont font,String text,float x,float y,Color c){
        font.setColor(c);font.draw(batch,text,x,y);
    }
    void draw(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font){
        float original=font.getData().scaleX;
        float scale=mobile?1.42f:1.16f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        box(shapes,0,0,1280,720,BG);
        box(shapes,0,640,1280,80,PANEL);
        box(shapes,35,604,1040,42,PANEL);
        box(shapes,1080,604,165,42,EDGE);
        box(shapes,1090,647,160,55,EDGE);
        for(int i=0;i<GameWikiCatalog.CATEGORIES.length;i++)
            box(shapes,35+(i%6)*204,555-(i/6)*43,198,38,
                    i==category?ACCENT:PANEL);
        box(shapes,30,105,390,401,PANEL);
        box(shapes,440,105,810,401,PANEL);
        for(int i=0;i<ROWS;i++){
            int idx=listPage*ROWS+i;
            if(idx<hits.size())box(shapes,35,462-i*49,375,44,
                    hits.get(idx)==chosen?EDGE:BG);
        }
        for(int i=0;i<4;i++){
            int x=switch(i){case 0->35;case 1->235;case 2->455;default->1065;};
            box(shapes,x,32,175,57,EDGE);
        }
        shapes.end();
        batch.begin();
        font.getData().setScale(mobile?1.62f:1.48f);
        label(batch,font,"FIELD ENCYCLOPEDIA",38,688,Color.WHITE);
        label(batch,font,"CLOSE",1110,681,Color.WHITE);
        font.getData().setScale(scale);
        label(batch,font,query.isEmpty()?"SEARCH - TAP OR TYPE TO FILTER":query,47,632,
                query.isEmpty()?MUTED:Color.WHITE);
        label(batch,font,"CLEAR",1110,631,Color.WHITE);
        font.getData().setScale(mobile?1.27f:1.04f);
        for(int i=0;i<GameWikiCatalog.CATEGORIES.length;i++)
            label(batch,font,GameWikiCatalog.CATEGORIES[i],
                    46+(i%6)*204,580-(i/6)*43,
                    i==category?Color.BLACK:Color.WHITE);
        font.getData().setScale(scale);
        for(int row=0;row<ROWS;row++){
            int idx=listPage*ROWS+row;
            if(idx>=hits.size())break;
            String name=hits.get(idx).title;
            int max=mobile?29:36;
            if(name.length()>max)name=name.substring(0,max-2)+"..";
            label(batch,font,name,45,491-row*49,
                    hits.get(idx)==chosen?ACCENT:Color.WHITE);
        }
        label(batch,font,"RESULTS "+hits.size()+" / "+catalog.size(),45,117,MUTED);
        if(chosen==null){
            label(batch,font,"NO MATCHES",468,474,Color.WHITE);
            label(batch,font,"CLEAR SEARCH OR CHANGE CATEGORY",468,432,MUTED);
        }else{
            String head=chosen.title;
            if(head.length()>61)head=head.substring(0,59)+"..";
            label(batch,font,head,463,483,Color.WHITE);
            font.getData().setScale(mobile?1.14f:1.04f);
            label(batch,font,chosen.category+"  /  "+chosen.id,464,454,ACCENT);
            font.getData().setScale(scale);
            int limit=visibleTextRows();
            for(int i=0;i<limit;i++){
                int index=scroll+i;
                if(index>=wrapped.size())break;
                label(batch,font,wrapped.get(index),465,424-i*(mobile?24:20),MUTED);
            }
        }
        font.getData().setScale(mobile?1.25f:1.14f);
        label(batch,font,"LIST PREV",44,67,Color.WHITE);
        label(batch,font,"LIST NEXT",244,67,Color.WHITE);
        label(batch,font,"READ UP",466,67,Color.WHITE);
        label(batch,font,"READ DOWN",1074,67,Color.WHITE);
        label(batch,font,"PAGE "+(listPage+1)+"/"+Math.max(1,(hits.size()+ROWS-1)/ROWS),
                684,66,ACCENT);
        font.setColor(Color.WHITE);
        font.getData().setScale(original);
        batch.end();
    }
}
