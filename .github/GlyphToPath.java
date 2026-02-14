import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.io.*;

public class GlyphToPath {
    public static void main(String[] args) throws Exception {
        String fontPath = args[0];

        Font font = Font.createFont(Font.TRUETYPE_FONT, new File(fontPath))
                        .deriveFont(Font.BOLD, 200f);

        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, "D");
        Shape shape = gv.getOutline();
        Rectangle2D bounds = shape.getBounds2D();

        // Center in 108x108 viewport (adaptive icon safe zone is ~66x66 in center)
        // We want the D centered and fitting within roughly 60x60 area in the middle
        double targetSize = 64.0;
        double scale = targetSize / Math.max(bounds.getWidth(), bounds.getHeight());

        double scaledW = bounds.getWidth() * scale;
        double scaledH = bounds.getHeight() * scale;
        double offsetX = (108 - scaledW) / 2.0 - bounds.getX() * scale;
        double offsetY = (108 - scaledH) / 2.0 - bounds.getY() * scale;

        AffineTransform tx = new AffineTransform();
        tx.translate(offsetX, offsetY);
        tx.scale(scale, scale);
        Shape transformed = tx.createTransformedShape(shape);

        StringBuilder pathData = new StringBuilder();
        PathIterator pi = transformed.getPathIterator(null);
        double[] coords = new double[6];
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    pathData.append(String.format("M%.2f,%.2f", coords[0], coords[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    pathData.append(String.format("L%.2f,%.2f", coords[0], coords[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    pathData.append(String.format("Q%.2f,%.2f %.2f,%.2f",
                            coords[0], coords[1], coords[2], coords[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    pathData.append(String.format("C%.2f,%.2f %.2f,%.2f %.2f,%.2f",
                            coords[0], coords[1], coords[2], coords[3],
                            coords[4], coords[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    pathData.append("Z");
                    break;
            }
            pi.next();
        }

        System.out.println(pathData.toString());
    }
}
