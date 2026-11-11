package diplomacy.client;

import diplomacy.net.BattleConnection;

/**
 * Render-only network session identity gate. Same-session reconnects may
 * interpolate continuously; a different authoritative session always drops
 * prior tracks and mirrors before the first new frame is observed.
 */
final class RemoteSessionState {
    private String sessionId;
    boolean apply(BattleConnection.Snapshot snapshot,
                  RemoteShipInterpolation interpolation,
                  RemoteFleetMirrors mirrors){
        if(snapshot==null||interpolation==null||mirrors==null)
            throw new IllegalArgumentException("Remote session state required");
        boolean changed=!snapshot.sessionId().equals(sessionId);
        if(changed){
            interpolation.reset();
            mirrors.reset();
            sessionId=snapshot.sessionId();
        }
        return changed;
    }
    String sessionId(){return sessionId;}
    void reset(RemoteShipInterpolation interpolation,RemoteFleetMirrors mirrors){
        if(interpolation!=null)interpolation.reset();
        if(mirrors!=null)mirrors.reset();
        sessionId=null;
    }
}
