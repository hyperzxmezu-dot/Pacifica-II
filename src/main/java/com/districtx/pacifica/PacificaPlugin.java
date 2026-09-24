package com.districtx.pacifica;

import com.districtx.pacifica.command.PacificaCommand;
import com.districtx.pacifica.command.TagCommand;
import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.api.combat.CombatTagAPI;
import com.districtx.pacifica.api.tag.TagAPI;
import com.districtx.pacifica.gui.GuiListener;
import com.districtx.pacifica.gui.GuiManager;
import com.districtx.pacifica.listener.PacificaListener;
import com.districtx.pacifica.service.EconomyManager;
import com.districtx.pacifica.service.CombatTagManager;
import com.districtx.pacifica.service.HologramManager;
import com.districtx.pacifica.service.StatusManager;
import com.districtx.pacifica.service.TeleportManager;
import com.districtx.pacifica.service.TpaManager;
import com.districtx.pacifica.service.WarpManager;
import com.districtx.pacifica.service.WastedManager;
import com.districtx.pacifica.service.BackpackManager;
import com.districtx.pacifica.service.SafeSpawnRegion;
import com.districtx.pacifica.service.TabManager;
import com.districtx.pacifica.service.TagManager;
import com.districtx.pacifica.service.TagGuiManager;
import com.districtx.pacifica.service.TagItemService;
import com.districtx.pacifica.service.PlaceholderService;
import com.districtx.pacifica.service.SeparateInventoryManager;
import com.districtx.pacifica.service.EssentialsService;
import com.districtx.pacifica.service.AfkManager;
import com.districtx.pacifica.service.ChatService;
import com.districtx.pacifica.listener.SeparateInventoryListener;
import com.districtx.pacifica.database.ItemPriceDatabase;
import com.districtx.pacifica.database.PlayerDatabase;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PacificaPlugin extends JavaPlugin {
    private ConfigStore store;
    private StatusManager statusManager;
    private TeleportManager teleportManager;
    private WarpManager warpManager;
    private EconomyManager economyManager;
    private CombatTagManager combatTagManager;
    private TpaManager tpaManager;
    private HologramManager hologramManager;
    private GuiManager guiManager;
    private WastedManager wastedManager;
    private BackpackManager backpackManager;
    private SafeSpawnRegion safeSpawnRegion;
    private TabManager tabManager;
    private TagManager tagManager;
    private TagGuiManager tagGuiManager;
    private TagItemService tagItemService;
    private boolean crackShotEnabled;
    private boolean weaponMechanicsEnabled;
    private PlaceholderService placeholderService;
    private SeparateInventoryManager separateInventoryManager;
    private EssentialsService essentialsService;
    private AfkManager afkManager;
    private ChatService chatService;
    private PlayerDatabase playerDatabase;
    private ItemPriceDatabase itemPriceDatabase;

    @Override
    public void onEnable() {
        playerDatabase = new PlayerDatabase(this);
        playerDatabase.backup();
        playerDatabase.initialize();
        itemPriceDatabase = new ItemPriceDatabase(this);
        itemPriceDatabase.backup();
        itemPriceDatabase.initialize();
        store = new ConfigStore(this, playerDatabase);
        playerDatabase.migrateLegacyData(store);
        itemPriceDatabase.migrateLegacyPrices(store);
        placeholderService = new PlaceholderService(this);
        crackShotEnabled = Bukkit.getPluginManager().isPluginEnabled("CrackShot");
        weaponMechanicsEnabled = Bukkit.getPluginManager().isPluginEnabled("WeaponMechanics");
        safeSpawnRegion = new SafeSpawnRegion(this);
        statusManager = new StatusManager(this);
        teleportManager = new TeleportManager(this);
        warpManager = new WarpManager(store);
        economyManager = new EconomyManager(this);
        combatTagManager = new CombatTagManager(this);
        tpaManager = new TpaManager(this);
        hologramManager = new HologramManager(this);
        guiManager = new GuiManager();
        wastedManager = new WastedManager(this);
        backpackManager = new BackpackManager(this);
        tabManager = new TabManager(this);
        tagManager = new TagManager(this);
        tagGuiManager = new TagGuiManager(this);
        tagItemService = new TagItemService(this);
        separateInventoryManager = new SeparateInventoryManager(this);
        essentialsService = new EssentialsService(this);
        afkManager = new AfkManager(this);
        chatService = new ChatService(this);
        Bukkit.getServicesManager().register(TaxiAPI.class, new PacificaTaxiService(this), this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(CombatTagAPI.class, new PacificaCombatTagService(this), this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(TagAPI.class, new PacificaTagService(this), this, org.bukkit.plugin.ServicePriority.Normal);

        PacificaCommand command = new PacificaCommand(this);
        for (String name : new String[]{"spawn", "warp", "setspawn", "setsafespawn", "rndmwarpset", "taxi", "trashcan", "tpa", "tpaccept", "tpdeny", "tpcancel", "ct", "pacificareload", "heal", "feed", "invsee", "afk", "ec", "enderchest", "gms", "gma", "gmc", "gmsp"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(command);
                getCommand(name).setTabCompleter(command);
            }
        }
        TagCommand tagCommand = new TagCommand(this);
        for (String name : new String[]{"tag", "tagadmin"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(tagCommand);
                getCommand(name).setTabCompleter(tagCommand);
            }
        }
        Bukkit.getPluginManager().registerEvents(new GuiListener(guiManager), this);
        Bukkit.getPluginManager().registerEvents(new PacificaListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.CombatTagListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ModerationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.BackpackListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.TabListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ServerListListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SeparateInventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.AfkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.TagListener(this), this);
        hologramManager.load();
        tagManager.start();
        tabManager.start();
    }

    @Override
    public void onDisable() {
        if (tpaManager != null) tpaManager.clear();
        if (teleportManager != null) teleportManager.clear();
        if (combatTagManager != null) combatTagManager.clear();
        if (wastedManager != null) wastedManager.clear();
        if (tabManager != null) tabManager.stop();
        if (tagManager != null) tagManager.stop();
        if (separateInventoryManager != null) separateInventoryManager.saveOnlinePlayers();
        if (store != null) store.save();
        if (itemPriceDatabase != null) itemPriceDatabase.close();
        if (playerDatabase != null) playerDatabase.close();
    }

    public ConfigStore store() { return store; }
    public StatusManager statuses() { return statusManager; }
    public TeleportManager teleports() { return teleportManager; }
    public WarpManager warps() { return warpManager; }
    public EconomyManager economy() { return economyManager; }
    public CombatTagManager combatTags() { return combatTagManager; }
    public TpaManager tpa() { return tpaManager; }
    public HologramManager holograms() { return hologramManager; }
    public GuiManager guis() { return guiManager; }
    public WastedManager wasted() { return wastedManager; }
    public BackpackManager backpacks() { return backpackManager; }
    public SafeSpawnRegion safeSpawn() { return safeSpawnRegion; }
    public boolean crackShotEnabled() { return crackShotEnabled; }
    public boolean weaponMechanicsEnabled() { return weaponMechanicsEnabled; }
    public TabManager tabs() { return tabManager; }
    public TagManager tags() { return tagManager; }
    public TagGuiManager tagGui() { return tagGuiManager; }
    public TagItemService tagItems() { return tagItemService; }
    public PlaceholderService placeholders() { return placeholderService; }
    public SeparateInventoryManager separateInventories() { return separateInventoryManager; }
    public EssentialsService essentials() { return essentialsService; }
    public AfkManager afk() { return afkManager; }
    public ChatService chats() { return chatService; }
    public PlayerDatabase playerDatabase() { return playerDatabase; }
    public ItemPriceDatabase itemPriceDatabase() { return itemPriceDatabase; }
    public void reload() {
        if (separateInventoryManager != null) separateInventoryManager.saveOnlinePlayers();
        store.reload();
        if (tabManager != null) { tabManager.stop(); tabManager.start(); }
        if (tagManager != null) { tagManager.stop(); tagManager.start(); }
        if (tagManager != null) tagManager.refreshAll();
    }
}