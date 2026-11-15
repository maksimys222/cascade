package com.maximus.cascade;

import java.util.Locale;

/** Красивые большие числа: 1.25K, 3.4M, 7.1B, … */
public final class Num {
	private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

	public static String fmt(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return "∞";
		}
		if (Math.abs(v) < 1000) {
			return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
		}
		int tier = (int) (Math.log10(Math.abs(v)) / 3);
		if (tier >= SUFFIX.length) {
			return String.format(Locale.ROOT, "%.2e", v);
		}
		double scaled = v / Math.pow(1000, tier);
		String n = scaled >= 100 ? String.format(Locale.ROOT, "%.0f", scaled)
			: scaled >= 10 ? String.format(Locale.ROOT, "%.1f", scaled)
			: String.format(Locale.ROOT, "%.2f", scaled);
		return n + SUFFIX[tier];
	}

	/** Секунды → «1ч 5м», «3м 10с». */
	public static String time(long seconds) {
		long h = seconds / 3600;
		long m = seconds % 3600 / 60;
		long s = seconds % 60;
		if (h > 0) {
			return h + "ч " + m + "м";
		}
		if (m > 0) {
			return m + "м " + s + "с";
		}
		return s + "с";
	}

	private Num() {
	}
}
