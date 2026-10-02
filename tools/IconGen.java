import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Genera el icono del mod (128x128, estilo pixel art) en src/main/resources/assets/socialmod/icon.png.
 * Uso: {@code java tools/IconGen.java}
 */
public final class IconGen {
    public static void main(String[] args) throws IOException {
        int scale = 8;
        String[] pixels = {
                "................",
                "..############..",
                ".#GGGGGGGGGGGG#.",
                ".#GGGGGGGGGGGG#.",
                ".#GG#GG#GG#GGG#.",
                ".#GG#GG#GG#GGG#.",
                ".#GGGGGGGGGGGG#.",
                ".#GGGGGGGGGGGG#.",
                "..#####GG#####..",
                "......#GG#......",
                ".....#GG#..WW...",
                "....#GG#..WWWW..",
                "....###..WWWWWW.",
                ".........W.WW.W.",
                "...........WW...",
                "................",
        };
        BufferedImage image = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Color color = switch (pixels[y].charAt(x)) {
                    case '#' -> new Color(0x1E5A1E);
                    case 'G' -> new Color(0x55FF55);
                    case 'W' -> new Color(0xFFAA00);
                    default -> null;
                };
                if (color != null) {
                    g.setColor(color);
                    g.fillRect(x * scale, y * scale, scale, scale);
                }
            }
        }
        // Puntos del globo en oscuro (los "..." de un mensaje)
        g.setColor(new Color(0x1E5A1E));
        for (int x : new int[]{4, 7, 10}) {
            g.fillRect(x * scale, 4 * scale, scale, 2 * scale);
        }
        g.dispose();
        File out = new File("src/main/resources/assets/socialmod/icon.png");
        out.getParentFile().mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println("Icono generado: " + out.getPath());
    }
}
