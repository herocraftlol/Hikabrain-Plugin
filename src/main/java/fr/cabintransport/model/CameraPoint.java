package fr.cabintransport.model;

/** Un point de caméra et la durée de transition vers le suivant. */
public class CameraPoint {
    private final PointRef point;
    private int transitionTicks;

    public CameraPoint(PointRef point, double transitionSeconds) {
        this.point = point;
        this.transitionTicks = Math.max(1, (int) Math.round(transitionSeconds * 20.0));
    }

    public PointRef getPoint() { return point; }
    public int getTransitionTicks() { return transitionTicks; }
    public double getTransitionSeconds() { return transitionTicks / 20.0; }
    public void setTransitionSeconds(double seconds) { transitionTicks = Math.max(1, (int) Math.round(seconds * 20.0)); }
}
