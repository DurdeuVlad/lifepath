// Passive observer: logs crack-animation stages broadcast by the server.
const mineflayer = require("mineflayer");
const bot = mineflayer.createBot({
	host: "localhost", port: 25565, username: "Watcher", version: "1.21.1",
});
bot.on("blockBreakProgressObserved", (block, stage) => {
	console.log("CRACK t=" + Date.now() + " stage=" + stage
			+ " " + block.name + " @" + block.position);
});
bot.on("blockUpdate", (old, now) => {
	if (now && now.name === "air" && old && old.name !== "air")
		console.log("BREAK t=" + Date.now() + " " + old.name + " @" + old.position);
});
bot.once("spawn", () => console.log("WATCHER SPAWNED " + bot.entity.position));
bot.on("kicked", (r) => console.log("KICKED " + JSON.stringify(r)));
bot.on("error", (e) => console.log("ERROR " + e.message));
bot.on("end", () => process.exit(0));
