package com.districtx.pacifica;

import com.districtx.pacifica.command.PacificaCommand;
import com.districtx.pacifica.command.TagCommand;
import com.districtx.pacifica.command.JailCommand;
import com.districtx.pacifica.command.BribeCommand;
import com.districtx.pacifica.command.BountyTrackCommand;
import com.districtx.pacifica.command.JobNpcCommand;
import com.districtx.pacifica.command.AdminPiiCommand;
import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.api.combat.CombatTagAPI;
import com.districtx.pacifica.api.tag.TagAPI;
import com.districtx.pacifica.api.jobs.JobAPI;
import com.districtx.pacifica.api.jobs.PacificaJobsAPI;
import com.districtx.pacifica.api.jobs.WantedAPI;
import com.districtx.pacifica.api.jobs.WantedDisplayAPI;
import com.districtx.pacifica.api.jobs.JailAPI;
import com.districtx.pacifica.api.jobs.BountyAPI;
import com.districtx.pacifica.api.jobs.MedicAPI;
import com.districtx.pacifica.api.jobs.JobMenuAPI;
import com.districtx.pacifica.api.supplydrops.SupplyDropsAPI;
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
import com.districtx.pacifica.service.EssentialsService;
import com.districtx.pacifica.service.AfkManager;
import com.districtx.pacifica.service.ChatService;
import com.districtx.pacifica.service.IdentityDisplayResolver;
import com.districtx.pacifica.service.JobWantedManager;
import com.districtx.pacifica.service.JailManager;
import com.districtx.pacifica.service.ArrestManager;
import com.districtx.pacifica.service.BountyManager;
import com.districtx.pacifica.service.MedicManager;
import com.districtx.pacifica.service.JobMenuManager;
import com.districtx.pacifica.service.SupplyDropsManager;
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
    private EssentialsService essentialsService;
    private AfkManager afkManager;
    private ChatService chatService;
    private IdentityDisplayResolver identityDisplayResolver;
    private JobWantedManager jobWantedManager;
    private JailManager jailManager;
    private ArrestManager arrestManager;
    private BountyManager bountyManager;
    private MedicManager medicManager;
    private JobMenuManager jobMenuManager;
    private SupplyDropsManager supplyDropsManager;
    private PlayerDatabase playerDatabase;
    private ItemPriceDatabase itemPriceDatabase;
    private PacificaPaths paths;

    @Override
    public void onEnable() {
        paths().migrateLegacyItemPriceDatabase(getLogger());
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
        identityDisplayResolver = new IdentityDisplayResolver(this);
        jobWantedManager = new JobWantedManager(this);
        jobWantedManager.start();
        jailManager = new JailManager(this);
        jailManager.start();
        arrestManager = new ArrestManager(this);
        arrestManager.start();
        bountyManager = new BountyManager(this);
        bountyManager.start();
        medicManager = new MedicManager(this);
        jobMenuManager = new JobMenuManager(this);
        supplyDropsManager = new SupplyDropsManager(this);
        tagGuiManager = new TagGuiManager(this);
        tagItemService = new TagItemService(this);
        essentialsService = new EssentialsService(this);
        afkManager = new AfkManager(this);
        chatService = new ChatService(this);
        Bukkit.getServicesManager().register(TaxiAPI.class, new PacificaTaxiService(this), this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(CombatTagAPI.class, new PacificaCombatTagService(this), this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(TagAPI.class, new PacificaTagService(this), this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(JobAPI.class, jobWantedManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(WantedAPI.class, jobWantedManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(WantedDisplayAPI.class, jobWantedManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(PacificaJobsAPI.class, jobWantedManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(JailAPI.class, jailManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(com.districtx.pacifica.api.jobs.ArrestAPI.class, arrestManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(BountyAPI.class, bountyManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(MedicAPI.class, medicManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(JobMenuAPI.class, jobMenuManager, this, org.bukkit.plugin.ServicePriority.Normal);
        Bukkit.getServicesManager().register(SupplyDropsAPI.class, supplyDropsManager, this, org.bukkit.plugin.ServicePriority.Normal);

        PacificaCommand command = new PacificaCommand(this);
        for (String name : new String[]{"spawn", "warp", "taxi", "trashcan", "tpa", "tpaccept", "tpdeny", "tpcancel", "ct", "heal", "feed", "invsee", "afk", "ec", "enderchest", "gms", "gma", "gmc", "gmsp"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(command);
                getCommand(name).setTabCompleter(command);
            }
        }
        TagCommand tagCommand = new TagCommand(this);
        for (String name : new String[]{"tag"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(tagCommand);
                getCommand(name).setTabCompleter(tagCommand);
            }
        }
        BribeCommand bribeCommand = new BribeCommand(this);
        if (getCommand("bribe") != null) {
            getCommand("bribe").setExecutor(bribeCommand);
            getCommand("bribe").setTabCompleter(bribeCommand);
        }
        BountyTrackCommand bountyTrackCommand = new BountyTrackCommand(this);
        if (getCommand("bountytrack") != null) {
            getCommand("bountytrack").setExecutor(bountyTrackCommand);
            getCommand("bountytrack").setTabCompleter(bountyTrackCommand);
        }
        com.districtx.pacifica.command.SupplyCommand supplyCommand = new com.districtx.pacifica.command.SupplyCommand(this);
        if (getCommand("supply") != null) {
            getCommand("supply").setExecutor(supplyCommand);
        }
        AdminPiiCommand adminPiiCommand = new AdminPiiCommand(this);
        if (getCommand("adminpii") != null) {
            getCommand("adminpii").setExecutor(adminPiiCommand);
            getCommand("adminpii").setTabCompleter(adminPiiCommand);
        }
        Bukkit.getPluginManager().registerEvents(new GuiListener(guiManager), this);
        Bukkit.getPluginManager().registerEvents(jobMenuManager, this);
        Bukkit.getPluginManager().registerEvents(supplyDropsManager, this);
        Bukkit.getPluginManager().registerEvents(jobWantedManager, this);
        Bukkit.getPluginManager().registerEvents(jailManager, this);
        Bukkit.getPluginManager().registerEvents(arrestManager, this);
        Bukkit.getPluginManager().registerEvents(bountyManager, this);
        Bukkit.getPluginManager().registerEvents(medicManager, this);
        Bukkit.getPluginManager().registerEvents(new PacificaListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.CombatTagListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ModerationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.BackpackListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.TabListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ServerListListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.AfkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.districtx.pacifica.listener.TagListener(this), this);
        hologramManager.load();
        tagManager.start();
        tabManager.start();
        identityDisplayResolver.start();
        supplyDropsManager.start();
    }

    @Override
    public void onDisable() {
        if (tpaManager != null) tpaManager.clear();
        if (teleportManager != null) teleportManager.clear();
        if (combatTagManager != null) combatTagManager.clear();
        if (wastedManager != null) wastedManager.clear();
        if (jobWantedManager != null) jobWantedManager.stop();
        if (jailManager != null) jailManager.stop();
        if (arrestManager != null) arrestManager.stop();
        if (bountyManager != null) bountyManager.stop();
        if (tabManager != null) tabManager.stop();
        if (tagManager != null) tagManager.stop();
        if (identityDisplayResolver != null) identityDisplayResolver.stop();
        if (supplyDropsManager != null) supplyDropsManager.stop();
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
    public EssentialsService essentials() { return essentialsService; }
    public AfkManager afk() { return afkManager; }
    public ChatService chats() { return chatService; }
    /** @return the shared Tag-over-level identity display resolver */
    public IdentityDisplayResolver identity() { return identityDisplayResolver; }
    public JobWantedManager jobsAndWanted() { return jobWantedManager; }
    public WantedDisplayAPI wantedDisplay() { return jobWantedManager; }
    public JailManager jails() { return jailManager; }
    public ArrestManager arrests() { return arrestManager; }
    public BountyManager bounties() { return bountyManager; }
    public MedicManager medics() { return medicManager; }
    public JobMenuManager jobMenus() { return jobMenuManager; }
    public SupplyDropsManager supplyDrops() { return supplyDropsManager; }
    public PlayerDatabase playerDatabase() { return playerDatabase; }
    public ItemPriceDatabase itemPriceDatabase() { return itemPriceDatabase; }
    public PacificaPaths paths() {
        if (paths == null) paths = new PacificaPaths(this);
        return paths;
    }
    public void reload() {
        store.reload();
        if (tabManager != null) { tabManager.stop(); tabManager.start(); }
        if (tagManager != null) { tagManager.stop(); tagManager.start(); }
        if (tagManager != null) tagManager.refreshAll();
        if (supplyDropsManager != null) supplyDropsManager.reload();
    }
}