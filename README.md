# 🏴 GhostyClaim

Feature-rich, modern land claiming plugin for all types of Minecraft survival servers.

Supports Minecraft 1.21+ (Bukkit, Spigot & Paper).

# ✨ Features

Torch-based claiming — place 4 Redstone Torches to define your claim corners
Guided world border shows the maximum claimable area
Session timeout with animated countdown
Full inventory GUI for managing claims
Admin GUI for server staff
Economy integration via Vault — charge per block, flat fee, or both
Configurable refund percentage on claim deletion
Rank-based claim limits via LuckPerms meta key, permission nodes, or config fallback
Per-claim flags: TNT, fire spread, PvP, mob spawning, explosions, creeper damage, leaf decay, ice/snow melt, animal damage, guest interaction
Trust system — let players build, break, and interact in your claim
Real-time particle border visualizer (DUST, FLAME, END_ROD, HAPPY_VILLAGER)
Claim expansion — expand an existing claim, only the price difference is charged
Dual database support: flat YAML or MySQL with connection pooling
Player name sync — automatically updates owner names on username change
Supports [ICODE]&[/ICODE] and HEX color codes ([ICODE]&#RRGGBB[/ICODE])
Full English & German translations
Fully customizable language files


# 📦 Dependencies

Vault -> https://www.spigotmc.org/resources/vault.34315/
LuckPerms -> https://luckperms.net
Essentials -> https://essentialsx.net


# 🧾 Commands

Command	Description	Permission
/claim	Create a new claim or enter expansion mode if standing in your own claim	ghostyclaim.claim.create
/claim expand	Explicitly start claim expansion mode	ghostyclaim.claim.expand
/claim menu	Open the claim management GUI	ghostyclaim.use
/claim list	List all your claims	ghostyclaim.use
/claim delete	Delete the claim you are standing in	ghostyclaim.claim.delete
/claim trust <player>	Trust a player in your claim	ghostyclaim.claim.trust
/claim untrust <player>	Remove a player's trust	ghostyclaim.claim.trust
/claim flags	Open the flags GUI for your claim	ghostyclaim.claim.flags
/claim rename <name>	Rename your claim	ghostyclaim.use
/claim visualize	Toggle the particle border visualizer	ghostyclaim.claim.visualize
/claim info	Display claim information	ghostyclaim.use
/claim cancel	Cancel an active claim creation or expansion session	ghostyclaim.use
/claim admin [player]	Open the admin GUI, optionally filtered by player	ghostyclaim.admin.gui
/claimreload	Reload the plugin configuration	ghostyclaim.admin.reload


# ⚙️ Installation

Download the latest release
Place the [ICODE].jar[/ICODE] into [ICODE]/plugins/[/ICODE]
Restart your server
Configure [ICODE]config.yml[/ICODE] and set your preferred language ([ICODE]en[/ICODE] or [ICODE]de[/ICODE])


# 🔄 Compatibility

Bukkit
Spigot
Paper


# 🛠️ Support

Found a bug?
Please include plugin version, Minecraft version & server software
and open a ticket on our Discord:
https://discord.gg/YTCHWAap2z
