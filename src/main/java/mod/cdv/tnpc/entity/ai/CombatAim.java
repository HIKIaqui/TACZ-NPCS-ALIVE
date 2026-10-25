package mod.cdv.tnpc.entity.ai;

import mod.cdv.tnpc.data.CombatSettings;

public final class CombatAim {
    public record Offset(float pitch, float yaw) {}
    private CombatAim() {}
    public static double multiplier(CombatSettings.Profile profile, double distance) {
        var points = profile.accuracyByDistance();
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        for (int i = 1; i < points.size(); i++) if (distance <= points.get(i).distance()) {
            var a = points.get(i - 1); var b = points.get(i);
            double fraction = (distance - a.distance()) / (b.distance() - a.distance());
            return a.spreadMultiplier() + fraction * (b.spreadMultiplier() - a.spreadMultiplier());
        }
        return points.getLast().spreadMultiplier();
    }
    /** Uniform angular disk around the aim direction, plus TaCZ's untouched weapon dispersion. */
    public static Offset offset(CombatSettings.Profile profile, double distance, double radiusRoll, double angleRoll) {
        double radius = profile.baseAimErrorDegrees() * multiplier(profile, distance) * Math.sqrt(radiusRoll);
        double angle = angleRoll * Math.PI * 2;
        return new Offset((float)(Math.sin(angle) * radius), (float)(Math.cos(angle) * radius));
    }
}
