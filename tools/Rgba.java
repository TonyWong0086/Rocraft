import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Studio export (raw RGBA dumps + text meshes + scripts) -> private legacy folder with PNGs. */
public class Rgba {
	public static void main(String[] a) throws Exception {
		Path root = Path.of(a[0]), out = Path.of(a[1]);
		Pattern rgba = Pattern.compile("\\.rgba(\\d+)x(\\d+)$");
		try (var s = Files.walk(root)) {
			for (Path p : s.filter(Files::isRegularFile).toList()) {
				String rel = root.relativize(p).toString().replace('\\', '/');
				var m = rgba.matcher(rel);
				boolean img = m.find();
				Path dest = out.resolve(img ? rel.substring(0, m.start()) : rel);
				Files.createDirectories(dest.getParent());
				if (img) {
					int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
					byte[] b = Files.readAllBytes(p);
					var bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					for (int i = 0; i < w * h; i++)
						bi.setRGB(i % w, i / w, (b[i * 4 + 3] & 255) << 24 | (b[i * 4] & 255) << 16 | (b[i * 4 + 1] & 255) << 8 | (b[i * 4 + 2] & 255));
					ImageIO.write(bi, "png", dest.toFile());
				} else Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
