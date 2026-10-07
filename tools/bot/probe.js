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
			case "inv": {
				// full component dump — lore/custom_data/quality visible
				for (const i of bot.inventory.items()) {
					console.log("ITEM " + i.name + "x" + i.count
							+ " comps=" + JSON.stringify(i.components || i.nbt || {}));
				}
				break;
			}
			case "craft": {
				// craft <itemName> [count] — needs materials in inventory;
				// uses a crafting table within reach if the recipe requires one
				const name = rest.join(" ").replace(/ /g, "_");
				const mcData = require("minecraft-data")(bot.version);
				const item = mcData.itemsByName[name];
				if (!item) { console.log("CRAFT unknown item " + name); break; }
				const recipes = bot.recipesFor(item.id, null, 1, null);
				const all = bot.recipesAll ? bot.recipesAll(item.id, null, null) : [];
				console.log("CRAFT dbg recipes=" + recipes.length + " all=" + all.length
						+ " ver=" + bot.version);
				if (!recipes.length) { console.log("CRAFT no recipe " + name); break; }
				const needsTable = recipes[0].requiresTable;
				let table = null;
				if (needsTable) {
					table = bot.findBlock({
						matching: (b) => b.name === "crafting_table",
						maxDistance: 8,
					});
					if (!table) { console.log("CRAFT no table near"); break; }
				}
				try {
					await bot.craft(recipes[0], 1, table);
					console.log("CRAFTED " + name);
				} catch (e) { console.log("CRAFT fail " + e.message); }
				break;
			}
			case "furnace_open": {
				const f = bot.findBlock({
					matching: (b) => ["furnace", "smoker", "blast_furnace"].includes(b.name),
					maxDistance: 8,
				});
				if (!f) { console.log("FURNACE none near"); break; }
				try {
					bot._furnace = await bot.openFurnace(f);
					console.log("FURNACE opened " + f.name + " " + f.position);
				} catch (e) { console.log("FURNACE open fail " + e.message); }
				break;
			}
			case "furnace_put": {
				// furnace_put input|fuel|output <item> [count]
				const slot = rest[0];
				const name = (rest[1] || "").replace(/ /g, "_");
				const count = parseInt(rest[2] || "64", 10);
				const it = bot.inventory.items().find((i) => i.name === name);
				if (!it) { console.log("FURNACE no item " + name); break; }
				const f = bot._furnace;
				if (!f) { console.log("FURNACE not open"); break; }
				try {
					if (slot === "input") await f.putInput(it.type, null, count);
					else if (slot === "fuel") await f.putFuel(it.type, null, count);
					console.log("FURNACE put " + slot + " " + name + "x" + Math.min(count, it.count));
				} catch (e) { console.log("FURNACE put fail " + e.message); }
				break;
			}
			case "furnace_take": {
				const f = bot._furnace;
				if (!f) { console.log("FURNACE not open"); break; }
				try {
					const out = f.outputItem();
					if (!out) { console.log("FURNACE output empty"); break; }
					await f.takeOutput();
					console.log("FURNACE took " + out.name + "x" + out.count);
				} catch (e) { console.log("FURNACE take fail " + e.message); }
				break;
			}
			case "furnace_state": {
				const f = bot._furnace;
				if (!f) { console.log("FURNACE not open"); break; }
				const o = f.outputItem();
				const inp = f.inputItem();
				console.log("FURNACE in=" + (inp ? inp.name + "x" + inp.count : "empty")
						+ " out=" + (o ? o.name + "x" + o.count : "empty")
						+ " progress=" + f.progress);
				break;
			}
			case "anvil_open": {
				const a = bot.findBlock({
					matching: (b) => /anvil/.test(b.name),
					maxDistance: 8,
				});
				if (!a) { console.log("ANVIL none near"); break; }
				try {
					bot._anvil = await bot.openAnvil(a);
					console.log("ANVIL opened " + a.position);
				} catch (e) { console.log("ANVIL open fail " + e.message); }
				break;
			}
			case "anvil_combine": {
				// anvil_combine <item1> <item2> [newName] — takes result,
				// prints xp-level delta (the real charged cost)
				const n1 = (rest[0] || "").replace(/ /g, "_");
				const n2 = (rest[1] || "").replace(/ /g, "_");
				const name = rest[2] || null;
				const a = bot._anvil;
				if (!a) { console.log("ANVIL not open"); break; }
				const i1 = bot.inventory.items().find((i) => i.name === n1);
				const i2 = bot.inventory.items().find((i) => i.name === n2);
				if (!i1 || !i2) { console.log("ANVIL missing " + n1 + "/" + n2); break; }
				const before = bot.experience.level;
				try {
					await a.combine(i1, i2, name);
					console.log("ANVIL combined xp " + before + " -> " + bot.experience.level);
				} catch (e) { console.log("ANVIL combine fail " + e.message); }
				break;
			}
			case "anvil_close": {
				if (bot._anvil) { bot._anvil.close(); bot._anvil = null; }
				console.log("ANVIL closed");
				break;
			}
			case "dig": {
				// dig <blockName> — break nearest matching block
				const name = (rest[0] || "").replace(/ /g, "_");
				const b = bot.findBlock({
					matching: (bl) => bl && bl.name === name,
					maxDistance: 8,
				});
				if (!b) { console.log("DIG none " + name); break; }
				try {
					await bot.dig(b);
					console.log("DUG " + name + " at " + b.position);
				} catch (e) { console.log("DIG fail " + name + ": " + e.message); }
				break;
			}
			case "xp":
				console.log("XP level=" + bot.experience.level
						+ " points=" + bot.experience.points);
				break;
			case "face": {
				// face <x> <y> <z> — point the camera at a block position
				const [x, y, z] = rest.map(Number);
				await bot.lookAt(new (require("vec3"))(x, y, z));
				console.log("FACED " + x + " " + y + " " + z);
				break;
			}
			case "fish": {
				// fish — needs rod in hand + water in reach; bot.fish() casts,
				// waits for the bite, reels. Prints the caught stack if it
				// landed in inventory (drop spawn is server-side either way).
				const rod = bot.inventory.items().find((i) => i.name === "fishing_rod");
				if (!rod) { console.log("FISH no rod"); break; }
				await bot.equip(rod, "hand");
				try {
					await Promise.race([bot.fish(),
						new Promise((_, rej) => setTimeout(
							() => rej(new Error("fish timeout")), 45000))]);
					const got = bot.inventory.items().at(-1);
					console.log("FISHED got " + (got ? got.name + "x" + got.count : "unknown"));
				} catch (e) { console.log("FISH fail " + e.message); }
				break;
			}
			case "shoot": {
				// shoot — draw held bow at nearest entity and release.
				// Damage source = projectile → exercises the archery path.
				const bow = bot.inventory.items().find((i) => i.name === "bow");
				if (!bow) { console.log("SHOOT no bow"); break; }
				const e = bot.nearestEntity((en) => en.name !== bot.username
						&& !["item", "projectile", "experience_orb", "player"]
							.includes(en.type)
						&& !["experience_orb"].includes(en.name));
				if (!e) { console.log("SHOOT no target"); break; }
				await bot.equip(bow, "hand");
				await bot.lookAt(e.position.offset(0, e.height * 0.5, 0));
				bot.activateItem();
				await new Promise((r) => setTimeout(r, 1150));
				bot.deactivateItem();
				console.log("SHOT at " + e.name);
				break;
			}
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
