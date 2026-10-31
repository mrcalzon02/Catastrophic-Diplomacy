package diplomacy.shipyard;

/** Android API26-compatible lowercase hex encoder; identical to HexFormat.of().formatHex. */
final class HexCodec {
    private HexCodec(){}
    static String hex(byte[] raw){
        if(raw==null)throw new IllegalArgumentException("No digest bytes");
        StringBuilder encoded=new StringBuilder(raw.length*2);
        for(byte value:raw){
            encoded.append(Character.forDigit((value>>>4)&15,16));
            encoded.append(Character.forDigit(value&15,16));
        }
        return encoded.toString();
    }
}
