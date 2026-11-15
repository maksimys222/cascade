package com.maximus.cascade;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.sun.net.httpserver.HttpServer;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;

/** Встроенный мини-веб-сервер, раздающий ресурспак Каскада, и отправка пака игрокам. */
public class PackServer {
	private final Cascade plugin;
	private byte[] zip;
	private String sha1;
	private HttpServer http;

	public PackServer(Cascade plugin) {
		this.plugin = plugin;
	}

	public void start() {
		try (InputStream in = plugin.getResource("cascade-pack.zip")) {
			if (in == null) {
				plugin.getLogger().warning("В jar нет cascade-pack.zip — меню навыков будет без графики");
				return;
			}
			zip = in.readAllBytes();
			sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
			int port = plugin.getConfig().getInt("pack.port", 8165);
			http = HttpServer.create(new InetSocketAddress(port), 0);
			http.createContext("/pack.zip", ex -> {
				ex.getResponseHeaders().add("Content-Type", "application/zip");
				ex.sendResponseHeaders(200, zip.length);
				try (OutputStream out = ex.getResponseBody()) {
					out.write(zip);
				}
			});
			http.start();
			plugin.getLogger().info("Ресурспак раздаётся на порту " + port + " (" + zip.length / 1024 + " КБ)");
		} catch (IOException | java.security.NoSuchAlgorithmException e) {
			plugin.getLogger().severe("Не удалось запустить раздачу ресурспака: " + e.getMessage());
		}
	}

	public void stop() {
		if (http != null) {
			http.stop(0);
		}
	}

	public void send(Player p) {
		if (zip == null) {
			return;
		}
		String url = plugin.getConfig().getString("pack.public-url", "http://localhost:8165/pack.zip");
		ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(sha1.getBytes()), URI.create(url), sha1);
		p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
			.packs(info)
			.required(plugin.getConfig().getBoolean("pack.required", false))
			.prompt(Text.c("&6Каскад &7— графика меню"))
			.build());
	}
}
