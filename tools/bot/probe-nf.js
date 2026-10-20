// One-shot bucket-pickup probe for the NeoForge lab (localhost:25566).
const mineflayer = require("mineflayer");
const bot = mineflayer.createBot({
	host: "localhost", port: 25566, username: "NfProbe", version: "1.21.1",
});
bot.on("messagestr", (m) => console.log("CHAT " + m));
bot.on("kicked", (r) => console.log("KICKED " + JSON.stringify(r)));
bot.on("error", (e) => console.log("ERROR " + e.message));
bot.once("spawn", async () => {
	console.log("SPAWNED " + bot.entity.position);
	await new Promise((r) => setTimeout(r, 1200));
	const e = bot.nearestEntity((en) => en.name !== bot.username
			&& en.type !== "item" && en.name !== "experience_orb");
	const bucket = bot.inventory.items().find((i) => i.name === "water_bucket");
	console.log("bucket=" + (bucket ? "yes" : "no") + " nearest=" + (e ? e.name : "none"));
	if (e && bucket) {
		await bot.equip(bucket, "hand");
		await bot.lookAt(e.position.offset(0, e.height * 0.5, 0));
		bot.activateEntity(e);
	}
	setTimeout(() => {
		console.log("inv=" + bot.inventory.items().map((i) => i.name).join(","));
		bot.quit();
	}, 2000);
});
bot.on("end", () => process.exit(0));
