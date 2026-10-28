package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Military-industrial command-console UI; approved 2026-10-27.
 * Procedural, native libGDX chrome: no texture allocations or crop assumptions.
 * All existing tap areas, keyboard actions and gameplay semantics are unchanged.
 */
final class MilitaryUiTheme {
    private MilitaryUiTheme() {}
    static final Color BACKGROUND=new Color(.014f,.020f,.027f,1f);
    static final Color PANEL=new Color(.060f,.078f,.088f,1f);
    static final Color SELECTION=new Color(.10f,.30f,.37f,1f);
    static final Color MUTED_TEXT=new Color(.66f,.72f,.75f,1f);
    static final Color FRAME=new Color(.20f,.225f,.23f,1f);
    static final Color FRAME_BRIGHT=new Color(.34f,.36f,.34f,1f);
    static final Color FRAME_SHADOW=new Color(.028f,.033f,.036f,1f);
    static final Color SCREEN=new Color(.023f,.038f,.046f,1f);
    static final Color BUTTON_FACE=new Color(.078f,.115f,.13f,1f);
    static final Color BUTTON_SELECTED=new Color(.11f,.20f,.22f,1f);
    static final Color AMBER=new Color(.98f,.57f,.17f,1f);
    static final Color TELEMETRY=new Color(.22f,.69f,.84f,1f);
    static final Color READY=new Color(.22f,.72f,.44f,1f);
    static final Color WARNING=new Color(.96f,.66f,.20f,1f);
    static final Color DANGER=new Color(.83f,.24f,.24f,1f);
    static final Color RIVET=new Color(.37f,.39f,.36f,1f);

    enum Intent { NORMAL, PRIMARY, CONFIRM, WARNING, DANGER }

    private static void block(ShapeRenderer shapes,float x,float y,float w,float h,Color color){
        if(w<=0||h<=0)return;
        shapes.setColor(color);
        shapes.rect(x,y,w,h);
    }

    /** The outer rail doesn't intrude into a screen's native content area. */
    static void canvas(ShapeRenderer shapes,float width,float height){
        canvas(shapes,width,height,false);
    }
    static void canvas(ShapeRenderer shapes,float width,float height,boolean textured){
        if(!textured)block(shapes,0,0,width,height,BACKGROUND);
        block(shapes,0,height-8,width,8,FRAME_SHADOW);
        block(shapes,8,height-5,width-16,2,FRAME_BRIGHT);
        block(shapes,0,0,5,height,FRAME_SHADOW);
        block(shapes,width-5,0,5,height,FRAME_SHADOW);
        block(shapes,9,9,width-18,2,FRAME);
        block(shapes,9,12,140,2,AMBER);
        for(int i=0;i<5;i++)
            block(shapes,23+i*32,height-5,8,1,RIVET);
    }

    /** Recessed screen with metal perimeter and small noninteractive rivets. */
    static void panel(ShapeRenderer shapes,float x,float y,float w,float h){
        if(w<32||h<32){block(shapes,x,y,w,h,PANEL);return;}
        block(shapes,x,y,w,h,FRAME_SHADOW);
        block(shapes,x+1,y+1,w-2,h-2,FRAME);
        block(shapes,x+3,y+3,w-6,h-6,SCREEN);
        block(shapes,x+5,y+h-8,w-10,2,FRAME_BRIGHT);
        block(shapes,x+6,y+5,w-12,1,FRAME_SHADOW);
        block(shapes,x+8,y+h-11,Math.min(68,w-16),2,AMBER);
        block(shapes,x+7,y+7,3,3,RIVET);
        block(shapes,x+w-10,y+7,3,3,RIVET);
        block(shapes,x+7,y+h-10,3,3,RIVET);
        block(shapes,x+w-10,y+h-10,3,3,RIVET);
    }

    /** Semantic trim leaves the label and the full functional hit box intact. */
    static void button(ShapeRenderer shapes,float x,float y,float w,float h,
                       boolean selected,Intent intent){
        if(w<12||h<12){block(shapes,x,y,w,h,selected?SELECTION:BUTTON_FACE);return;}
        Color accent=switch(intent){
            case PRIMARY -> AMBER;
            case CONFIRM -> READY;
            case WARNING -> WARNING;
            case DANGER -> DANGER;
            case NORMAL -> selected?TELEMETRY:FRAME_BRIGHT;
        };
        block(shapes,x,y,w,h,FRAME_SHADOW);
        block(shapes,x+1,y+1,w-2,h-2,FRAME);
        block(shapes,x+3,y+3,w-6,h-6,selected?BUTTON_SELECTED:BUTTON_FACE);
        block(shapes,x+4,y+h-6,w-8,2,accent);
        if(selected||intent!=Intent.NORMAL)
            block(shapes,x+4,y+5,3,h-12,accent);
        else
            block(shapes,x+5,y+5,2,h-12,FRAME_SHADOW);
        block(shapes,x+8,y+6,3,2,RIVET);
        block(shapes,x+w-11,y+6,3,2,RIVET);
    }
}
