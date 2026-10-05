// Lifepath dev-server probe bot (offline mode).
// Joins localhost:25565. Command interface: drop *.txt files into tools/bot/cmd/
// (one command per line). Commands:
//   hold <itemName>   equip inventory item in main hand
//   use               activate held item (right-click)
//   attack            attack nearest entity
//   chat <text>       send chat/command
//   status            print health/food/pos/uuid/abilities
//   quit              disconnect
const mineflayer = require("mineflayer");
const fs = require("fs");
const path = require("path");

const CMDDIR = path.join(__dirname, "cmd");
fs.mkdirSync(CMDDIR, { recursive: true });

const bot = mineflayer.createBot({
	host: "localhost",
	port: 25565,
	username: "M19Probe",
	version: "1.21.1",
});

bot.once("spawn", () => {
	console.log("BOT SPAWNED uuid=" + bot.player.uuid + " pos=" + bot.entity.position);
});

bot.on("messagestr", (msg) => console.log("CHAT " + msg));
bot._client.on("custom_payload", (p) => {
	console.log("PAYLOAD " + p.channel + " "
			+ Buffer.from(p.data || []).toString("hex").slice(0, 160));
});
bot.on("death", () => console.log("BOT DIED"));
bot.on("respawn", () => console.log("BOT RESPAWNED health=" + bot.health + " food=" + bot.food));
bot.on("health", () => console.log("BOT HEALTH " + bot.health + " food=" + bot.food));
bot.on("kicked", (r) => console.log("BOT KICKED " + JSON.stringify(r)));
bot.on("error", (e) => console.log("BOT ERROR " + e.message));
bot.on("end", (r) => { console.log("BOT ENDED " + r); process.exit(0); });

async function cmd(line) {
	const [op, ...rest] = line.trim().split(/\s+/);
	try {
		switch (op) {
			case "hold": {
				const item = bot.inventory.items().find((i) => i.name.includes(rest.join(" ")));
				if (!item) { console.log("HOLD none " + rest.join(" ")); break; }
				await bot.equip(item, "hand");
				console.log("HELD " + item.name);
				break;
			}
			case "use":
				bot.activateItem();
				console.log("USED " + (bot.heldItem ? bot.heldItem.name : "empty"));
				break;
			case "attack": {
				const e = bot.nearestEntity((en) => en.name !== bot.username);
				if (!e) { console.log("ATTACK none"); break; }
				bot.attack(e);
				console.log("ATTACKED " + e.name + " " + e.uuid);
				break;
			}
			case "look": {
				const e = bot.nearestEntity((en) => en.name !== bot.username);
				if (e) await bot.lookAt(e.position.offset(0, e.height * 0.5, 0));
				console.log("LOOKED " + (e ? e.name : "none"));
				break;
			}
			case "chat":
				bot.chat(rest.join(" "));
				console.log("CHATTED " + rest.join(" "));
				break;
			case "send": {
				// send <channel> <resourcelocation-arg> — one utf8 string body
				const channel = rest[0];
				const arg = rest[1] || "";
				const body = Buffer.concat([
					Buffer.from([arg.length]), Buffer.from(arg, "utf8")]);
				bot._client.write("custom_payload", { channel, data: body });
				console.log("SENT " + channel + " " + arg);
				break;
			}
			case "status":
				console.log("STATUS health=" + bot.health + " food=" + bot.food
						+ " pos=" + bot.entity.position + " onGround=" + bot.entity.onGround
						+ " abilities=" + JSON.stringify(bot.abilities)
						+ " inv=" + bot.inventory.items().map((i) => i.name + "x" + i.count).join(","));
				break;
			case "quit":
				bot.quit();
				break;
			default:
				console.log("UNKNOWN " + op);
		}
	} catch (e) {
		console.log("CMD ERROR " + op + ": " + e.message);
	}
}

setInterval(() => {
	let files;
	try { files = fs.readdirSync(CMDDIR).filter((f) => f.endsWith(".txt")).sort(); }
	catch { return; }
	for (const f of files) {
		const fp = path.join(CMDDIR, f);
		try {
			const lines = fs.readFileSync(fp, "utf8").split("\n");
			fs.unlinkSync(fp);
			(async () => { for (const l of lines) { if (l.trim()) await cmd(l); } })();
		} catch { /* partial write, retry next tick */ }
	}
}, 250);
