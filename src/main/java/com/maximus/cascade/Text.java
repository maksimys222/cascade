package com.maximus.cascade;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Текст с цветами через &: «&aЗелёный &lжирный». */
public final class Text {
	private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

	public static Component c(String s) {
		return AMP.deserialize(s).decoration(TextDecoration.ITALIC, false);
	}

	public static List<Component> lines(String... lines) {
		List<Component> out = new ArrayList<>();
		for (String l : lines) {
			out.add(c(l));
		}
		return out;
	}

	private Text() {
	}
}
