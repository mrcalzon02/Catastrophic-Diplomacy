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
        var aft=d.impact(-1,0,-.8,0,100,Battle.DamageType.PLASMA);
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
        same.impact(-1,0,-.8,0,100,Battle.DamageType.PLASMA);
        ok(same.fingerprint()==d.fingerprint(),"deterministic replay");
        boolean rejected=false;
        try{d.addArmor(DirectionalDefense.Facing.AFT,Double.NaN);}
        catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"reject nonfinite upgrades");
        System.out.println("PASS directional shield/armor/structure: 4 facings, tiles, typed damage and determinism");
    }
}
