package diplomacy.sim;

/** No graphics dependency. Confirms local tile damage, facing isolation and power-gated shielding. */
public final class DirectionalDefenseTest {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void close(double actual,double expected,String message){
        if(StrictMath.abs(actual-expected)>1e-8)throw new AssertionError(message+": "+actual+" != "+expected);
    }
    public static void main(String[] args){
        DirectionalDefense d=new DirectionalDefense(100,80,200);
        close(d.shields(DirectionalDefense.Facing.FORWARD),25,"quartered shield");
        close(d.armor(DirectionalDefense.Facing.PORT),20,"quartered armor");
        ok(DirectionalDefense.facingFor(1,0)==DirectionalDefense.Facing.FORWARD,"bow");
        ok(DirectionalDefense.facingFor(0,1)==DirectionalDefense.Facing.PORT,"port");
        ok(DirectionalDefense.facingFor(-1,0)==DirectionalDefense.Facing.AFT,"stern");
        ok(DirectionalDefense.facingFor(0,-1)==DirectionalDefense.Facing.STARBOARD,"starboard");
        d.addArmor(DirectionalDefense.Facing.FORWARD,40);
        close(d.armor(DirectionalDefense.Facing.FORWARD),60,"front-only armor module");
        close(d.armor(DirectionalDefense.Facing.AFT),20,"aft remains untouched");
        d.addShield(DirectionalDefense.Facing.PORT,12);
        close(d.shields(DirectionalDefense.Facing.PORT),37,"port-only shield module");
        long first=d.fingerprint();
        var front=d.impact(1,0,.8,0,5,Battle.DamageType.LASER);
        ok(front.facing()==DirectionalDefense.Facing.FORWARD,"correct shield facing");
        close(front.armorConsumed(),0,"shield blocks laser before armor");
        ok(d.shields(DirectionalDefense.Facing.FORWARD)<25,"forward shield absorbs");
        close(d.shields(DirectionalDefense.Facing.AFT),25,"aft shield unaffected");
        ok(d.fingerprint()!=first,"fingerprint changes on damage");
        double before=d.shields(DirectionalDefense.Facing.FORWARD);
        close(d.recharge(1,100,true),0,"damaged shield recharge delay");
        close(d.shields(DirectionalDefense.Facing.FORWARD),before,"delay suppresses regeneration");
        ok(d.recharge(1.1,100,true)>0,"powered shield refreshes after delay");
        d.setDamageFactor(DirectionalDefense.Layer.SHIELD,DirectionalDefense.Facing.AFT,
                Battle.DamageType.PLASMA,0.25);
        close(d.damageFactor(DirectionalDefense.Layer.SHIELD,DirectionalDefense.Facing.AFT,
                Battle.DamageType.PLASMA),0.25,"typed resistance");
        var aft=d.impact(-1,0,-.8,0,150,Battle.DamageType.PLASMA);
        ok(aft.hullDamage()>0,"armor collapse reveals structural/hull damage");
        ok(d.tile(aft.row(),aft.column()).exposure()>0,"visible localized armor loss");
        close(d.shields(DirectionalDefense.Facing.PORT),37,"another shield facing unaffected");
        var same=new DirectionalDefense(100,80,200);
        same.addArmor(DirectionalDefense.Facing.FORWARD,40);
        same.addShield(DirectionalDefense.Facing.PORT,12);
        same.impact(1,0,.8,0,5,Battle.DamageType.LASER);
        same.recharge(1,100,true);same.recharge(1.1,100,true);
        same.setDamageFactor(DirectionalDefense.Layer.SHIELD,DirectionalDefense.Facing.AFT,
                Battle.DamageType.PLASMA,0.25);
        same.impact(-1,0,-.8,0,150,Battle.DamageType.PLASMA);
        ok(same.fingerprint()==d.fingerprint(),"deterministic replay");
        // One actual physical splash center may damage multiple mechanical
        // armor cells, but must not create multiple visual strike centers.
        var splash=new DirectionalDefense(0,120,240);
        var strike=splash.impactSplash(1,0,.25,.05,12,
                Battle.DamageType.LASER,0,1.15);
        int affected=0,marked=0;
        for(int r=0;r<16;r++)for(int c=0;c<16;c++){
            var tile=splash.tile(r,c);
            if(tile.exposure()>1e-6)affected++;
            if(tile.directImpact()){
                marked++;
                ok(r==strike.row()&&c==strike.column(),
                        "Splash neighbors must NOT fabricate impact locations");
            }
        }
        ok(affected>1,"Fixture needs real mechanical splash spreading");
        ok(marked==1,"One weapon impact should produce one damage mark");
        ok(splash.tile(strike.row(),strike.column()).impactType()==Battle.DamageType.LASER,
                "The actual direct weapon family must be recorded");
        String persisted=splash.saveIntegrity();
        var reloaded=new DirectionalDefense(0,120,240);
        reloaded.restoreIntegrity(persisted);
        ok(reloaded.saveIntegrity().equals(persisted),
                "Impact locations AND weapon families must survive save/load");
        ok(reloaded.tile(strike.row(),strike.column()).impactType()==Battle.DamageType.LASER,
                "Saved actual weapon provenance lost after reload");
        int bytes=4*8+16*16*2*8;
        byte[] old=java.util.Base64.getUrlDecoder().decode(persisted);
        String legacy=java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOf(old,bytes));
        // Last-generation saves carried directImpact bits but no type.
        String oldMarks=java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOf(old,bytes+16*16/8));
        var previous=new DirectionalDefense(0,120,240);
        previous.restoreIntegrity(oldMarks);
        ok(previous.tile(strike.row(),strike.column()).directImpact()&&
                previous.tile(strike.row(),strike.column()).impactType()==null,
                "Old impact-bit saves must preserve legacy untyped visual behavior");
        var restoredOld=new DirectionalDefense(0,120,240);
        restoredOld.restoreIntegrity(legacy);
        int inferred=0;
        for(int r=0;r<16;r++)for(int c=0;c<16;c++)
            if(restoredOld.tile(r,c).directImpact())inferred++;
        ok(inferred==1,"Legacy splash should infer ONE mark, not one per worn tile");
        // Every supported family must survive a direct hit and restore.
        for(Battle.DamageType family:Battle.DamageType.values()){
            var sample=new DirectionalDefense(0,120,240);
            var impact=sample.impactSplash(1,0,.25,.05,12,family,0,1.15);
            var tile=sample.tile(impact.row(),impact.column());
            ok(tile.directImpact()&&tile.impactType()==family,
                    "Weapon family was not registered at the real hit center: "+family);
            var clone=new DirectionalDefense(0,120,240);
            clone.restoreIntegrity(sample.saveIntegrity());
            ok(clone.tile(impact.row(),impact.column()).impactType()==family,
                    "Weapon family changed after save/load: "+family);
        }
        reloaded.overhaul();
        for(int r=0;r<16;r++)for(int c=0;c<16;c++)
            ok(!reloaded.tile(r,c).directImpact()&&
                    reloaded.tile(r,c).impactType()==null,
                    "Overhaul must clear physical impact markers AND weapon families");
        boolean rejected=false;
        try{d.addArmor(DirectionalDefense.Facing.AFT,Double.NaN);}
        catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"reject nonfinite upgrades");
        System.out.println("PASS directional shield/armor/structure: 4 facings, tiles, typed damage and determinism");
    }
}
