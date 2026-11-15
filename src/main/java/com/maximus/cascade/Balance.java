package com.maximus.cascade;

/** Все числа баланса в одном месте. */
public final class Balance {
	public static final int SPEED_MAX = 7;

	/** Множитель шага каскада: 1-й ×1, 2-й ×2, 3-й ×4 … 10-й ×60, дальше +15 за шаг. */
	private static final int[] MULT = {1, 1, 2, 4, 7, 12, 18, 25, 35, 45, 60};

	public static int baseMult(int step) {
		return step < MULT.length ? MULT[step] : 60 + (step - 10) * 15;
	}

	/** Множитель шага с учётом навыков. */
	public static double mult(PlayerData d, int step) {
		Skills.Stats s = Skills.stats(d);
		double m = baseMult(s.resonance && step > 0 ? step + 1 : step);
		if (step >= 5) {
			m *= 1 + s.lateBoostPct;
		}
		return m * (1 + s.multPct);
	}

	public static String stepName(int step) {
		if (step >= 10) {
			return "&d&lЛЕГЕНДАРНЫЙ КАСКАД!";
		}
		if (step >= 7) {
			return "&c&lКаскад-шторм!";
		}
		if (step >= 5) {
			return "&6&lНевероятно!";
		}
		return switch (step) {
			case 4 -> "&eВеликолепно!";
			case 3 -> "&aОтлично!";
			default -> "&bКаскад!";
		};
	}

	public static long capacity(PlayerData d) {
		return Math.round(150 * Math.pow(1.5, d.bagLevel) * (1 + Skills.stats(d).bagPct));
	}

	/** Скидка на улучшения от навыков (не больше 50%). */
	private static double price(PlayerData d, double base) {
		return Math.round(base * (1 - Math.min(0.5, Skills.stats(d).discount)));
	}

	public static double bagCost(PlayerData d) {
		return price(d, 200 * Math.pow(1.9, d.bagLevel));
	}

	public static double valueMult(PlayerData d) {
		return (1 + 0.25 * d.valueLevel) * (1 + Skills.stats(d).valuePct);
	}

	public static double valueCost(PlayerData d) {
		return price(d, 150 * Math.pow(2.1, d.valueLevel));
	}

	/** Пауза между ударами в мс. */
	public static long cooldown(PlayerData d) {
		return Math.round(Math.max(100, 450 - 50L * d.speedLevel) * (1 - Math.min(0.6, Skills.stats(d).speedPct)));
	}

	public static double speedCost(PlayerData d) {
		return price(d, 300 * Math.pow(2.6, d.speedLevel));
	}

	/** Опыт до следующего уровня. */
	public static double xpNeed(int level) {
		return Math.round(40 * Math.pow(level, 1.6));
	}

	private Balance() {
	}
}
