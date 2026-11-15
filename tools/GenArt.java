import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * Графика ресурспака Каскада и шрифт древа навыков.
 * Запуск: java tools/GenArt.java src/main/pack src/main/resources/tree.txt <minecraft-client.jar> src/main/resources/tree_glyphs.txt
 *
 * Круглая карта древа рисуется в заголовке окна: каждый узел (кольцо + иконка) и каждая линия — отдельный
 * символ шрифта cascade:gui со своим местом (ascent по вертикали, сдвиги пробелами по горизонтали).
 * Плагин читает tree_glyphs.txt (символ, x, ширина) и красит символы по прогрессу игрока.
 */
public class GenArt {
	static final int W = 176;
	static final int H = 222;
	/** Круглая карта: центр (в координатах окна) и радиусы рядов 0 (край) … 4 (корень). */
	static final double CX = 88;
	static final double CY = 111;
	static final double[] RADIUS = {126, 108, 90, 72, 54, 36};
	static final double STEP = 6;
	static final double CORE = 16;
	static final int ROWS = 6;
	/** Карта больше окна: фон 280×272, начинается на 52 px левее и 25 px выше окна. */
	static final int OV_X = -52;
	static final int OV_Y = -25;
	static final int OV_W = 280;
	static final int OV_H = 272;
	/** Прозрачный запас снизу у символов: иначе клиент не даст поднять их выше заголовка (ascent ≤ height). */
	static final int PAD = 48;

	record Node(String id, int index, int branch, int x, int y, char shape, String icon, String parent) {
	}

	record Branch(String name, Color color, String icon, List<Node> nodes) {
	}

	static final List<Branch> branches = new ArrayList<>();
	static final Map<String, Node> nodes = new LinkedHashMap<>();
	/** иконка → {модель, текстура}. */
	static final Map<String, String[]> icons = new HashMap<>();

	static File out;
	static final StringBuilder font = new StringBuilder();
	static final StringBuilder glyphs = new StringBuilder();
	static int warnings;

	public static void main(String[] args) throws Exception {
		out = new File(args[0], "assets/cascade/textures");
		new File(out, "gui").mkdirs();
		new File(out, "item").mkdirs();
		new File(out, "tree").mkdirs();
		parse(Files.readAllLines(new File(args[1]).toPath(), StandardCharsets.UTF_8));

		save(button("reset"), "item/btn_reset.png");
		save(arrow(true), "item/btn_left.png");
		save(arrow(false), "item/btn_right.png");
		save(emblem(), "item/emblem.png");
		save(disc(0.98f), "item/disc_fill.png");
		save(disc(0.78f), "item/disc_hole.png");

		// пробелы: −1…−256 (U+F801…F809) и +1…+256 (U+F821…F829)
		font.append("{\"providers\":[{\"type\":\"space\",\"advances\":{");
		for (int i = 0; i < 9; i++) {
			font.append(i == 0 ? "" : ",").append(String.format("\"\\u%04X\":%d,\"\\u%04X\":%d", 0xF801 + i, -(1 << i), 0xF821 + i, 1 << i));
		}
		font.append("}}");

		try (ZipFile jar = new ZipFile(args[2])) {
			for (int i = 0; i < branches.size(); i++) {
				save(branchPage(i, jar), "gui/branch" + i + ".png");
				bitmap("cascade:gui/branch" + i + ".png", 222, 13, 0xE000 + i);
				glyphs.append("bg ").append(i).append(String.format(" E%03X ", i)).append("0 ").append(W + 1).append('\n');
			}
			// клиент не берёт символы больше 256 px — фон карты из четырёх четвертей
			BufferedImage ov = overview();
			int hw = OV_W / 2;
			int hh = OV_H / 2;
			for (int q = 0; q < 4; q++) {
				int qx = (q % 2) * hw;
				int qy = (q / 2) * hh;
				BufferedImage part = ov.getSubimage(qx, qy, hw, hh);
				save(part, "gui/overview" + q + ".png");
				bitmap("cascade:gui/overview" + q + ".png", hh, 13 - (OV_Y + qy), 0xE006 + q);
				glyphs.append(String.format("bg -%d E%03X %d %d%n", q + 1, 6 + q, OV_X + qx, advance(part, 1)));
			}
			int[] sizes = {9, 11, 13};
			for (int s : sizes) {
				save(pad(ring(s)), "tree/ring" + s + ".png");
			}
			for (Node n : nodes.values()) {
				overviewNode(n, jar);
			}
			for (Node n : nodes.values()) {
				overviewLine(n);
				if (n.parent() != null) {
					pageLine(n);
				}
			}
		}
		font.append("]}");
		File fontFile = new File(args[0], "assets/cascade/font/gui.json");
		fontFile.getParentFile().mkdirs();
		Files.writeString(fontFile.toPath(), font.toString(), StandardCharsets.UTF_8);
		Files.writeString(new File(args[3]).toPath(), glyphs.toString(), StandardCharsets.UTF_8);
		System.out.println("ok: узлов " + nodes.size() + ", предупреждений " + warnings);
	}

	// ---------------- tree.txt ----------------

	static void parse(List<String> lines) {
		String[] head = null;
		List<String[]> small = new ArrayList<>();
		List<String[]> big = new ArrayList<>();
		List<String[]> key = new ArrayList<>();
		List<String> rows = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] t = line.split(" \\| ", 2)[0].split(" ");
			switch (t[0]) {
				case "icon" -> icons.put(t[1], new String[] {t[2], t[3]});
				case "branch" -> {
					head = t;
					small.clear();
					big.clear();
					key.clear();
					rows.clear();
				}
				case "small" -> small.add(t);
				case "big" -> big.add(t);
				case "key" -> key.add(t);
				case "row" -> {
					rows.add(t[1]);
					if (rows.size() == ROWS) {
						addBranch(head, small, big, key, rows);
					}
				}
				default -> throw new IllegalStateException(line);
			}
		}
	}

	static void addBranch(String[] head, List<String[]> small, List<String[]> big, List<String[]> key, List<String> rows) {
		int b = branches.size();
		List<Node> list = new ArrayList<>();
		int[] counters = new int[3];
		for (int y = ROWS - 1; y >= 0; y--) {
			for (int x = 0; x < 9; x++) {
				char c = rows.get(y).charAt(x);
				if (c == '.') {
					continue;
				}
				int kind = c == 'O' ? 1 : c == 'K' ? 2 : 0;
				List<String[]> pool = kind == 0 ? small : kind == 1 ? big : key;
				String icon = pool.get(counters[kind]++ % pool.size())[3];
				String parent = null;
				if (c != 'r') {
					String below = rows.get(y + 1);
					int best = -1;
					for (int px = 0; px < 9; px++) {
						if (below.charAt(px) != '.' && (best < 0 || Math.abs(px - x) < Math.abs(best - x)
							|| (Math.abs(px - x) == Math.abs(best - x) && Math.abs(px - 4) < Math.abs(best - 4)))) {
							best = px;
						}
					}
					parent = "b" + b + "r" + (y + 1) + "c" + best;
				}
				Node n = new Node("b" + b + "r" + y + "c" + x, nodes.size(), b, x, y, c == 'r' ? 'o' : c, icon, parent);
				list.add(n);
				nodes.put(n.id(), n);
			}
		}
		branches.add(new Branch(head[4], new Color(Integer.parseInt(head[2], 16)), head[3], list));
	}

	// ---------------- геометрия ----------------

	static double[] ovPos(Node n) {
		double a = Math.toRadians(-90 + 60 * n.branch() + (n.x() - 4) * STEP);
		return new double[] {CX + RADIUS[n.y()] * Math.cos(a), CY + RADIUS[n.y()] * Math.sin(a)};
	}

	static int ringSize(Node n) {
		return n.shape() == 'K' ? 13 : n.shape() == 'O' ? 11 : 9;
	}

	static double[] pagePos(Node n) {
		return new double[] {16 + 18 * n.x(), 26 + 18 * n.y()};
	}

	// ---------------- шрифт ----------------

	static void bitmap(String file, int height, int ascent, int ch) {
		if (ascent > height) {
			System.out.println("ВНИМАНИЕ: ascent " + ascent + " > height " + height + " у " + file);
			warnings++;
			ascent = height;
		}
		font.append(String.format(",{\"type\":\"bitmap\",\"file\":\"%s\",\"height\":%d,\"ascent\":%d,\"chars\":[\"\\u%04X\"]}",
			file, height, ascent, ch));
	}

	/** Ширина символа, как её считает клиент: до последнего непрозрачного столбца, +1. */
	static int advance(BufferedImage img, double scale) {
		int actual = 0;
		for (int x = img.getWidth() - 1; x >= 0 && actual == 0; x--) {
			for (int y = 0; y < img.getHeight(); y++) {
				if ((img.getRGB(x, y) >>> 24) != 0) {
					actual = x + 1;
					break;
				}
			}
		}
		return (int) (0.5 + actual * scale) + 1;
	}

	static void overviewNode(Node n, ZipFile jar) throws Exception {
		double[] p = ovPos(n);
		int s = ringSize(n);
		int rx = (int) Math.round(p[0] - s / 2.0);
		int ry = (int) Math.round(p[1] - s / 2.0);
		bitmap("cascade:tree/ring" + s + ".png", s + PAD, 13 - ry, 0xE100 + n.index());
		glyphs.append(String.format("ring %s E%03X %d %d%n", n.id(), 0x100 + n.index(), rx, s + 1));
		String[] ic = icons.get(n.icon());
		if (ic == null) {
			throw new IllegalStateException("Нет строки icon для " + n.icon());
		}
		int h = s - 2;
		String file = "tree/ic_" + n.icon() + "_" + h + ".png";
		BufferedImage icon = scaled(texture(jar, ic[1]), h);
		if (!new File(out, file).exists()) {
			save(pad(icon), file);
		}
		bitmap("cascade:" + file, h + PAD, 13 - (ry + 1), 0xE300 + n.index());
		glyphs.append(String.format("icon %s E%03X %d %d%n", n.id(), 0x300 + n.index(), rx + 1, advance(icon, 1)));
	}

	static void overviewLine(Node n) throws Exception {
		double[] b = ovPos(n);
		double[] a;
		double ra;
		if (n.parent() == null) {
			a = new double[] {CX, CY};
			ra = CORE;
		} else {
			Node p = nodes.get(n.parent());
			a = ovPos(p);
			ra = ringSize(p) / 2.0;
		}
		lineGlyph(a, ra, b, ringSize(n) / 2.0, "o" + n.index(), 0xE500 + n.index(), "oline " + n.id(), 1.3f);
	}

	static void pageLine(Node n) throws Exception {
		lineGlyph(pagePos(nodes.get(n.parent())), 6.5, pagePos(n), 6.5, "p" + n.index(), 0xE700 + n.index(), "pline " + n.id(), 1.6f);
	}

	static void lineGlyph(double[] a, double ra, double[] b, double rb, String file, int ch, String key, float width) throws Exception {
		double dx = b[0] - a[0];
		double dy = b[1] - a[1];
		double len = Math.hypot(dx, dy);
		double[] s = {a[0] + dx / len * ra, a[1] + dy / len * ra};
		double[] e = {b[0] - dx / len * rb, b[1] - dy / len * rb};
		int x0 = (int) Math.floor(Math.min(s[0], e[0])) - 1;
		int y0 = (int) Math.floor(Math.min(s[1], e[1])) - 1;
		int x1 = (int) Math.ceil(Math.max(s[0], e[0])) + 1;
		int y1 = (int) Math.ceil(Math.max(s[1], e[1])) + 1;
		BufferedImage img = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Double(s[0] - x0, s[1] - y0, e[0] - x0, e[1] - y0));
		g.dispose();
		save(pad(img), "tree/" + file + ".png");
		bitmap("cascade:tree/" + file + ".png", img.getHeight() + PAD, 13 - y0, ch);
		glyphs.append(String.format("%s E%03X %d %d%n", key, ch - 0xE000, x0, advance(img, 1)));
	}

	// ---------------- картинки ----------------

	/** Прозрачный запас снизу (см. PAD). */
	static BufferedImage pad(BufferedImage img) {
		BufferedImage res = new BufferedImage(img.getWidth(), img.getHeight() + PAD, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = res.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return res;
	}

	static BufferedImage texture(ZipFile jar, String path) throws Exception {
		var entry = jar.getEntry("assets/minecraft/textures/" + path + ".png");
		if (entry == null) {
			throw new IllegalStateException("Нет текстуры " + path);
		}
		try (InputStream in = jar.getInputStream(entry)) {
			BufferedImage img = ImageIO.read(in);
			// анимированные текстуры — полоса кадров, берём первый
			return img.getHeight() > img.getWidth() ? img.getSubimage(0, 0, img.getWidth(), img.getWidth()) : img;
		}
	}

	/** Уменьшение иконки до h×h с усреднением. */
	static BufferedImage scaled(BufferedImage src, int h) {
		BufferedImage res = new BufferedImage(h, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = res.createGraphics();
		g.drawImage(src.getScaledInstance(h, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
		g.dispose();
		return res;
	}

	static void save(BufferedImage img, String path) throws Exception {
		ImageIO.write(img, "png", new File(out, path));
	}

	static Graphics2D g(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}

	static Color alpha(Color c, int a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
	}

	static Color shade(Color c, float k) {
		return new Color(Math.min(255, (int) (c.getRed() * k)), Math.min(255, (int) (c.getGreen() * k)), Math.min(255, (int) (c.getBlue() * k)));
	}

	/** Кольцо узла: белая обводка (красится цветом состояния) и тёмная середина под иконку. */
	static BufferedImage ring(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		g.setColor(Color.WHITE);
		g.fill(new Ellipse2D.Double(0, 0, s, s));
		g.setColor(new Color(34, 30, 44));
		g.fill(new Ellipse2D.Double(1.3, 1.3, s - 2.6, s - 2.6));
		g.dispose();
		return img;
	}

	/** Круглая карта: тёмный круг-холст, 6 цветных секторов, кольца рядов, ядро. Координаты — от (OV_X, OV_Y). */
	static BufferedImage overview() {
		BufferedImage img = new BufferedImage(OV_W, OV_H, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(11);
		double cx = CX - OV_X;
		double cy = CY - OV_Y;
		double outer = RADIUS[0] + 10;
		for (int y = 0; y < OV_H; y++) {
			for (int x = 0; x < OV_W; x++) {
				double d = Math.hypot(x - cx, y - cy);
				if (d > outer + 4) {
					continue;
				}
				int n = r.nextInt(6);
				int a = d > outer ? (int) (255 * (outer + 4 - d) / 4) : 255;
				img.setRGB(x, y, new Color(16 + n, 14 + n, 23 + n, a).getRGB());
			}
		}
		Graphics2D g = g(img);
		for (int b = 0; b < branches.size(); b++) {
			Color c = branches.get(b).color();
			double a = -90 + 60 * b;
			g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) outer, new float[] {0f, 0.2f, 1f},
				new Color[] {alpha(c, 0), alpha(c, 72), alpha(c, 30)}));
			g.fill(new Arc2D.Double(cx - outer, cy - outer, outer * 2, outer * 2, -a - 30, 60, Arc2D.PIE));
		}
		g.setStroke(new BasicStroke(1f));
		for (int b = 0; b < 6; b++) {
			double a = Math.toRadians(-90 + 60 * b + 30);
			g.setColor(new Color(0, 0, 0, 120));
			g.draw(new Line2D.Double(cx + 18 * Math.cos(a), cy + 18 * Math.sin(a), cx + outer * Math.cos(a), cy + outer * Math.sin(a)));
		}
		for (double rad : RADIUS) {
			g.setColor(new Color(255, 255, 255, 13));
			g.draw(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
		}
		g.setColor(new Color(0xC8, 0xA8, 0x60, 120));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(new Ellipse2D.Double(cx - outer, cy - outer, outer * 2, outer * 2));
		g.setColor(new Color(20, 18, 28));
		g.fill(new Ellipse2D.Double(cx - CORE, cy - CORE, CORE * 2, CORE * 2));
		g.setColor(new Color(0xF0, 0xD0, 0x80));
		g.setStroke(new BasicStroke(1.6f));
		g.draw(new Ellipse2D.Double(cx - CORE, cy - CORE, CORE * 2, CORE * 2));
		g.setColor(new Color(0xF0, 0xD0, 0x80, 80));
		g.draw(new Ellipse2D.Double(cx - CORE - 4, cy - CORE - 4, (CORE + 4) * 2, (CORE + 4) * 2));
		// звезда в ядре
		Polygon star = new Polygon();
		for (int k = 0; k < 16; k++) {
			double a = Math.PI / 8 * k - Math.PI / 2;
			double rad = k % 2 == 0 ? 11 : 4;
			star.addPoint((int) Math.round(cx + rad * Math.cos(a)), (int) Math.round(cy + rad * Math.sin(a)));
		}
		g.setColor(new Color(0xF6, 0xDC, 0x8C));
		g.fill(star);
		g.dispose();
		return img;
	}

	/** Страница ветки: знамя на все 6 рядов сундука; ниже, на месте инвентаря, — кнопки и вкладки веток. */
	static BufferedImage branchPage(int i, ZipFile jar) throws Exception {
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(40 + i);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int n = r.nextInt(9);
				float vy = 1f - Math.abs(y - 70) / 260f;
				img.setRGB(x, y, new Color((int) ((30 + n) * vy), (int) ((26 + n) * vy), (int) ((26 + n) * vy)).getRGB());
			}
		}
		banner(img, new Random(90 + i), 5, 171, 3, 132, branches.get(i).color());
		Graphics2D g = g(img);
		g.setPaint(new RadialGradientPaint(88, 70, 90, new float[] {0f, 1f}, new Color[] {new Color(0, 0, 0, 95), new Color(0, 0, 0, 0)}));
		g.fillRect(0, 0, W, 132);
		g.setColor(new Color(0, 0, 0, 120));
		g.fillRect(0, 0, W, 14);
		// панель кнопок: ◆ ◀ [6 веток] ▶ — на первом ряду инвентаря
		g.setColor(new Color(12, 10, 9, 240));
		g.fillRect(3, 134, W - 6, H - 137);
		g.setColor(new Color(180, 150, 110, 90));
		g.drawRect(3, 134, W - 7, H - 138);
		int y = 140;
		for (int col = 0; col < 9; col++) {
			int x = 8 + col * 18;
			boolean tab = col >= 2 && col <= 7;
			Color c = tab ? branches.get(col - 2).color() : new Color(0xE8, 0xC0, 0x6A);
			boolean current = tab && col - 2 == i;
			g.setColor(current ? alpha(c, 120) : new Color(255, 255, 255, 14));
			g.fill(new Ellipse2D.Double(x - 1, y - 1, 17, 17));
			g.setColor(current ? shade(c, 1.3f) : alpha(c, 170));
			g.setStroke(new BasicStroke(current ? 1.6f : 1f));
			g.draw(new Ellipse2D.Double(x - 1, y - 1, 17, 17));
		}
		g.drawImage(emblem(), 8, y, 16, 16, null);
		g.drawImage(arrow(true), 26, y, 16, 16, null);
		g.drawImage(arrow(false), 152, y, 16, 16, null);
		for (int b = 0; b < branches.size(); b++) {
			String tex = icons.get(branches.get(b).icon())[1];
			g.drawImage(scaled(texture(jar, tex), 12), 8 + (b + 2) * 18 + 2, y + 2, null);
		}
		// хотбар
		for (int col = 0; col < 9; col++) {
			g.setColor(new Color(255, 255, 255, 16));
			g.fillRect(8 + col * 18 - 1, 197, 18, 18);
		}
		g.setColor(new Color(90, 76, 60));
		g.drawRect(0, 0, W - 1, H - 1);
		g.dispose();
		return img;
	}

	/** Рваное знамя из вертикальных мазков кисти. */
	static void banner(BufferedImage img, Random r, int x0, int x1, int y0, int y1, Color c) {
		int w = x1 - x0;
		int[] top = new int[w];
		int[] bottom = new int[w];
		for (int k = 0; k < w; k++) {
			double t = (k + 0.5) / w;
			top[k] = y0 + r.nextInt(4);
			bottom[k] = y1 - (int) (Math.pow(Math.abs(t - 0.5) * 2, 2) * 14) - r.nextInt(6);
		}
		for (int s = 0; s < w * 6; s++) {
			int k = r.nextInt(w);
			int width = 1 + r.nextInt(3);
			Color col = alpha(shade(c, 0.55f + r.nextFloat() * 0.5f), 70 + r.nextInt(110));
			int ys = top[k] + r.nextInt(6);
			int ye = bottom[k] - r.nextInt(10);
			for (int dx = 0; dx < width && k + dx < w; dx++) {
				for (int y = ys; y < ye; y++) {
					blend(img, x0 + k + dx, y, col);
				}
			}
		}
	}

	static void blend(BufferedImage img, int x, int y, Color c) {
		if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
			return;
		}
		Color b = new Color(img.getRGB(x, y), true);
		float a = c.getAlpha() / 255f;
		img.setRGB(x, y, new Color((int) (c.getRed() * a + b.getRed() * (1 - a)), (int) (c.getGreen() * a + b.getGreen() * (1 - a)),
			(int) (c.getBlue() * a + b.getBlue() * (1 - a)), 255).getRGB());
	}

	static void frame(BufferedImage img) {
		Graphics2D g = g(img);
		g.setColor(new Color(10, 8, 7, 235));
		g.fillRect(4, 131, W - 8, H - 135);
		g.setColor(new Color(180, 150, 110, 90));
		g.drawRect(4, 131, W - 9, H - 136);
		for (int row = 0; row < 4; row++) {
			int y = row < 3 ? 140 + row * 18 : 198;
			for (int col = 0; col < 9; col++) {
				int x = 8 + col * 18;
				g.setColor(new Color(255, 255, 255, 16));
				g.fillRect(x - 1, y - 1, 18, 18);
				g.setColor(new Color(255, 255, 255, 32));
				g.drawRect(x - 1, y - 1, 17, 17);
			}
		}
		g.setColor(new Color(90, 76, 60));
		g.drawRect(0, 0, W - 1, H - 1);
		g.dispose();
	}

	/** Круг для узлов в клетках: disc_fill белый (перекрашивается), disc_hole тёмный поверх — получается кольцо. */
	static BufferedImage disc(float r) {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		float rad = 32 * r;
		boolean hole = r < 0.9f;
		g.setColor(hole ? new Color(28, 25, 38) : Color.WHITE);
		g.fill(new Ellipse2D.Float(32 - rad, 32 - rad, rad * 2, rad * 2));
		g.dispose();
		return img;
	}

	static BufferedImage arrow(boolean left) {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		g.setColor(new Color(18, 16, 14, 235));
		g.fill(new Ellipse2D.Float(2, 2, 28, 28));
		g.setColor(new Color(235, 228, 215));
		g.setStroke(new BasicStroke(1.6f));
		g.draw(new Ellipse2D.Float(2.5f, 2.5f, 27, 27));
		g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (left) {
			g.drawLine(19, 9, 12, 16);
			g.drawLine(12, 16, 19, 23);
		} else {
			g.drawLine(13, 9, 20, 16);
			g.drawLine(20, 16, 13, 23);
		}
		g.dispose();
		return img;
	}

	static BufferedImage button(String kind) {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		Color c = new Color(0xE0, 0x6B, 0x6B);
		g.setColor(new Color(20, 24, 38, 230));
		g.fillRoundRect(2, 2, 28, 28, 8, 8);
		g.setColor(c);
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(2, 2, 27, 27, 8, 8);
		g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawArc(9, 9, 14, 14, 60, 280);
		g.fillPolygon(new int[] {20, 26, 19}, new int[] {6, 11, 13}, 3);
		g.dispose();
		return img;
	}

	static BufferedImage emblem() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = g(img);
		Color gold = new Color(0xE8, 0xC0, 0x6A);
		g.setColor(new Color(20, 18, 28));
		g.fill(new Ellipse2D.Float(1, 1, 30, 30));
		g.setColor(gold);
		g.setStroke(new BasicStroke(1.6f));
		g.draw(new Ellipse2D.Float(1.5f, 1.5f, 29, 29));
		Polygon star = new Polygon();
		for (int k = 0; k < 12; k++) {
			double a = Math.PI / 6 * k - Math.PI / 2;
			double rad = k % 2 == 0 ? 11 : 4.5;
			star.addPoint((int) Math.round(16 + rad * Math.cos(a)), (int) Math.round(16 + rad * Math.sin(a)));
		}
		g.fill(star);
		g.dispose();
		return img;
	}
}
