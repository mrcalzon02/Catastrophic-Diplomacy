package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws actual faction hull texture through jagged polygon cutouts.
 * Fragment triangles share the SOURCE UVs, so wings, hull plating, windows
 * and existing alpha masking fracture rather than turning into colored blobs.
 * Uses one bounded PolygonSpriteBatch and one reusable procedural smoke texture.
 */
final class ShipDeathRenderer implements Disposable {
    private static final int CLOUD_TEXTURE_SIZE=64;
    private final PolygonSpriteBatch polygons=new PolygonSpriteBatch(1400);
    private final Texture smoke;
    private final Map<Long,PolygonRegion[]> regions=new HashMap<>();
    private int lastFragments,lastSmoke,lastVisibleDeaths;

    ShipDeathRenderer(){
        Pixmap pixmap=new Pixmap(CLOUD_TEXTURE_SIZE,CLOUD_TEXTURE_SIZE,Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for(int y=0;y<CLOUD_TEXTURE_SIZE;y++)for(int x=0;x<CLOUD_TEXTURE_SIZE;x++){
            float px=(x+.5f-CLOUD_TEXTURE_SIZE*.5f)/(CLOUD_TEXTURE_SIZE*.5f);
            float py=(y+.5f-CLOUD_TEXTURE_SIZE*.5f)/(CLOUD_TEXTURE_SIZE*.5f);
            float angle=(float)Math.atan2(py,px);
            float wobble=1+.12f*(float)Math.sin(angle*5+.55)
                    +.10f*(float)Math.cos(angle*9-.4);
            float r=(float)Math.sqrt(px*px+py*py)/wobble;
            float density=Math.max(0,1-r);
            density=density*density*(3-2*density);
            // One reusable cloud mask; seeded puffs supply organic irregularity.
            pixmap.setColor(1,1,1,density);
            pixmap.drawPixel(x,y);
        }
        smoke=new Texture(pixmap);pixmap.dispose();
        smoke.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
    }

    private PolygonRegion[] geometry(ShipDeathTimeline.Burst burst,Texture texture){
        PolygonRegion[] existing=regions.get(burst.seed);
        if(existing!=null)return existing;
        PolygonRegion[] pieces=new PolygonRegion[burst.fragments.size()];
        TextureRegion sheet=new TextureRegion(texture);
        for(int i=0;i<pieces.length;i++){
            float[] unit=burst.fragments.get(i).vertices();
            float[] pixel=new float[unit.length];
            for(int v=0;v<unit.length;v+=2){
                pixel[v]=unit[v]*texture.getWidth();
                pixel[v+1]=unit[v+1]*texture.getHeight();
            }
            pieces[i]=new PolygonRegion(sheet,pixel,ShipDeathTimeline.PENTAGON_TRIANGLES);
        }
        regions.put(burst.seed,pieces);
        return pieces;
    }

    void render(ShipDeathTimeline timeline,TerranPlaceholderSprites sprites,
                OrthographicCamera camera,CombatViewBounds view,
                ShapeRenderer geometryFallback){
        lastFragments=lastSmoke=lastVisibleDeaths=0;
        // Drop polygon metadata immediately after its matching debris expires.
        java.util.Set<Long> present=new java.util.HashSet<>();
        for(ShipDeathTimeline.Burst burst:timeline.bursts())present.add(burst.seed);
        regions.keySet().removeIf(seed->!present.contains(seed));
        if(present.isEmpty())return;

        polygons.setProjectionMatrix(camera.combined);
        polygons.begin();
        for(ShipDeathTimeline.Burst burst:timeline.bursts()){
            var ship=burst.ship;
            float age=burst.age,scale=ship.scale();
            if(view!=null&&!view.circle(ship.x(),ship.y(),110*scale+age*60*scale))continue;
            lastVisibleDeaths++;
            float fraction=Math.min(1,age/ShipDeathTimeline.LIFETIME);
            float fade=(1-fraction);
            for(ShipDeathTimeline.Puff puff:burst.smoke){
                float growth=1+age*(1.05f+puff.drift()*.40f);
                float radius=puff.radius()*growth;
                float x=ship.x()+puff.offsetX()+puff.vx()*age*.70f;
                float y=ship.y()+puff.offsetY()+puff.vy()*age*.70f;
                if(view!=null&&!view.circle(x,y,radius))continue;
                polygons.setColor(.22f,.25f,.31f,.30f*fade*fade);
                polygons.draw(smoke,x-radius,y-radius,radius*2,radius*2);
                lastSmoke++;
            }
            // Hot reactor and escaping gases light the fractured center briefly.
            if(age<.32f){
                float radius=CombatEffectScale.of((75f+age*55)*scale);
                float alpha=(1-age/.32f)*.62f;
                polygons.setColor(1f,.46f,.15f,alpha);
                polygons.draw(smoke,ship.x()-radius,ship.y()-radius,
                        radius*2,radius*2);
            }
            if(age>=ShipDeathTimeline.FRAGMENT_LIFE)continue;
            Texture hull=sprites==null?null:sprites.sourceTexture(ship.faction(),ship.hull());
            if(hull==null)continue;
            drawFragments(burst,geometry(burst,hull),hull);
        }
        polygons.setColor(Color.WHITE);
        polygons.end();

        // Missing original artwork: fracture the same spatial hull into faceted
        // faction-colored shards, never borrow a different civilization's PNG.
        if(geometryFallback!=null){
            geometryFallback.setProjectionMatrix(camera.combined);
            boolean drawing=false;
            for(ShipDeathTimeline.Burst burst:timeline.bursts()){
                if(burst.age>=ShipDeathTimeline.FRAGMENT_LIFE)continue;
                var ship=burst.ship;
                if(view!=null&&!view.circle(ship.x(),ship.y(),
                        110*ship.scale()+burst.age*60*ship.scale()))continue;
                if(sprites!=null && sprites.sourceTexture(ship.faction(),ship.hull())!=null)
                    continue;
                if(!drawing){
                    geometryFallback.begin(ShapeRenderer.ShapeType.Filled);
                    drawing=true;
                }
                fallbackFragments(geometryFallback,burst);
            }
            if(drawing)geometryFallback.end();
        }
    }

    private void drawFragments(ShipDeathTimeline.Burst burst,
                               PolygonRegion[] regions,Texture texture){
        float elapsed=burst.age,scale=burst.ship.scale();
        float width=34*scale*texture.getWidth()/(float)texture.getHeight();
        float height=34*scale;
        float distance=(float)(1-Math.exp(-1.15f*elapsed))/1.15f;
        float fade=(float)Math.pow(Math.max(0,
                1-elapsed/ShipDeathTimeline.FRAGMENT_LIFE),.80);
        for(int i=0;i<regions.length;i++){
            var frag=burst.fragments.get(i);
            float x=burst.ship.x()-width*.5f+frag.vx()*distance;
            float y=burst.ship.y()-height*.5f+frag.vy()*distance;
            float rotation=(float)Math.toDegrees(burst.ship.facing())-90+
                    frag.spin()*elapsed;
            polygons.setColor(1,1,1,fade*frag.intensity());
            polygons.draw(regions[i],x,y,width*.5f,height*.5f,
                    width,height,1,1,rotation);
            lastFragments++;
        }
    }

    private void fallbackFragments(ShapeRenderer shapes,ShipDeathTimeline.Burst burst){
        float elapsed=burst.age,scale=burst.ship.scale();
        float width=34*scale,height=34*scale;
        float drift=(float)(1-Math.exp(-1.15f*elapsed))/1.15f;
        float fade=Math.max(0,1-elapsed/ShipDeathTimeline.FRAGMENT_LIFE);
        float[] points=new float[10];
        for(var frag:burst.fragments){
            float rotation=(float)Math.toRadians(Math.toDegrees(burst.ship.facing())-90+
                    frag.spin()*elapsed);
            float co=(float)Math.cos(rotation),si=(float)Math.sin(rotation);
            for(int v=0;v<10;v+=2){
                float lx=(frag.vertices()[v]-.5f)*width;
                float ly=(frag.vertices()[v+1]-.5f)*height;
                points[v]=burst.ship.x()+frag.vx()*drift+lx*co-ly*si;
                points[v+1]=burst.ship.y()+frag.vy()*drift+lx*si+ly*co;
            }
            shapes.setColor(burst.ship.faction().r*.8f,
                    burst.ship.faction().g*.8f,burst.ship.faction().b*.8f,
                    fade*frag.intensity());
            for(int t=0;t<ShipDeathTimeline.PENTAGON_TRIANGLES.length;t+=3){
                int a=ShipDeathTimeline.PENTAGON_TRIANGLES[t]*2;
                int b=ShipDeathTimeline.PENTAGON_TRIANGLES[t+1]*2;
                int c=ShipDeathTimeline.PENTAGON_TRIANGLES[t+2]*2;
                shapes.triangle(points[a],points[a+1],points[b],points[b+1],
                        points[c],points[c+1]);
                lastFragments++;
            }
        }
    }

    int fragmentsRendered(){return lastFragments;}
    int smokeRendered(){return lastSmoke;}
    int visibleDeaths(){return lastVisibleDeaths;}
    @Override public void dispose(){
        regions.clear();polygons.dispose();smoke.dispose();
    }
}
