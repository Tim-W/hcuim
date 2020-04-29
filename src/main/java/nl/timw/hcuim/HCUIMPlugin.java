package nl.timw.hcuim;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.vars.AccountType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.prefs.Preferences;

@Slf4j
@PluginDescriptor(
        name = "HCUIM"
)
public class HCUIMPlugin extends Plugin {
    private boolean isHCUIMLocal = false;
    private boolean isHCUIMLocalInitialized = false;
    private int hcuimIconIndex = 39;

    Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

    @Inject
    private Client client;

    @Inject
    private HCUIMConfig config;

    private WorldPoint lastDeath;

    private static final Set<Integer> RESPAWN_REGIONS = ImmutableSet.of(
            6457, // Kourend
            12850, // Lumbridge
            11828, // Falador
            12342, // Edgeville
            11062, // Camelot
            13150, // Prifddinas (it's possible to spawn in 2 adjacent regions)
            12894 // Prifddinas
    );

    private static final Set<Integer> TUTORIAL_ISLAND_REGIONS = ImmutableSet.of(12336, 12335, 12592, 12080, 12079, 12436);

    private void setXp() {
        if (client.getLocalPlayer() != null) {
            prefs.putInt("s1" + client.getLocalPlayer().getName(), Arrays.hashCode(client.getSkillExperiences()));
        }
    }

    private boolean checkXp() {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return false;
        int sum = 0;
        for (int skillExperience : client.getSkillExperiences()) {
            sum += skillExperience;
        }
        // Following 2 checks are for whether the player is still on tutorial island
        if (sum < 1500) {
            return true;
        }
        int xp = prefs.getInt("s1" + client.getLocalPlayer().getName(), -1);
        //This makes sure the player has not gained XP outside of RuneLite
        return xp == Arrays.hashCode(client.getSkillExperiences());
    }

    private boolean isHCUIM() {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return false;
        //Is HCUIM check: by default it is true, but afterwards do other checks to make sure it is correct
        boolean isHCUIMFromPrefs = prefs.getBoolean("s2" + client.getLocalPlayer().getName(), true);
        if (!isHCUIMFromPrefs) return false;
        //Check if the account is an UIM. If it is not, it may still be on tutorial island. Otherwise reject status
        if (!TUTORIAL_ISLAND_REGIONS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()) &&
                client.getAccountType() != AccountType.ULTIMATE_IRONMAN) {
            revokeHCUIMStatus();
            return false;
        }
        if (!checkXp()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "It seems that you obtained XP outside of RuneLite, therefore your Hardcore Ultimate Ironman status has been revoked.", null);
            revokeHCUIMStatus();
            return false;
        }
        isHCUIMLocal = true;
        return true;
    }

    private void loadHCUIMStatusIcon() {
        if (isHCUIM()) {
            IndexedSprite[] modIcons = client.getModIcons();
            IndexedSprite[] newArray = Arrays.copyOf(modIcons, modIcons.length + 1);
            String ICON_FILE_NAME = "purple.png";
            final IndexedSprite sprite = getIndexedSprite(ICON_FILE_NAME);
            hcuimIconIndex = modIcons.length;
            newArray[hcuimIconIndex] = sprite;
            client.setModIcons(newArray);
        }
    }

    private void updateChatbox(int iconIndex) {
        Widget chatboxTypedText = client.getWidget(WidgetInfo.CHATBOX_INPUT);

        if (chatboxTypedText == null || chatboxTypedText.isHidden()) {
            return;
        }

        String[] chatbox = chatboxTypedText.getText().split(":", 2);
        String rsn = Objects.requireNonNull(client.getLocalPlayer()).getName();

        chatboxTypedText.setText(getImgTag(iconIndex) + Text.removeTags(rsn) + ":" + chatbox[1]);
    }

    private String getImgTag(int i) {
        return "<img=" + i + ">";
    }

    private void revokeHCUIMStatus() {
        if (client.getLocalPlayer() == null) return;
        prefs.putBoolean("s2" + client.getLocalPlayer().getName(), false);
        isHCUIMLocal = false;
    }

    private IndexedSprite getIndexedSprite(String file) {
        try {
            log.debug("Loading: {}", file);
            BufferedImage image = ImageUtil.getResourceStreamFromClass(this.getClass(), file);
            return ImageUtil.getImageIndexedSprite(image, client);
        } catch (RuntimeException ex) {
            log.debug("Unable to load image: ", ex);
        }

        return null;
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        setXp();
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event) {
        if (isHCUIMLocal) {
            updateChatbox(hcuimIconIndex); // this stops flickering when typing
        }
    }

    @Subscribe
    public void onPlayerDeath(PlayerDeath playerDeath) {
        Player myPlayer = client.getLocalPlayer();

        //First check whether the player is an UIM and that it is the client player that died
        if (playerDeath.getPlayer() != myPlayer && client.getAccountType() != AccountType.ULTIMATE_IRONMAN) {
            return;
        }

        lastDeath = client.getLocalPlayer().getWorldLocation();
    }

    private boolean tobDeath() {
        return client.getVar(Varbits.THEATRE_OF_BLOOD) == 3;
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            //Calculate HCUIM status on every log-in
            isHCUIMLocalInitialized = false;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (lastDeath != null && !client.getLocalPlayer().getWorldLocation().equals(lastDeath)) {
            if (!RESPAWN_REGIONS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()) && !tobDeath()) {
                log.debug("You died, but you were in a safe area, but did not respawn in a known respawn location: {}",
                        client.getLocalPlayer().getWorldLocation().getRegionID());

                lastDeath = null;
                return;
            }
            if (isHCUIM()) {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You have fallen as a Hardcore Ultimate Iron Man, your Hardcore status has been revoked.", null);
                revokeHCUIMStatus();
            }
            //Currently we check that the player respawns in one of the respawn regions. This is a good indicator that the player died outside of a "safe" death.
            //TODO may be missing several other checks

            lastDeath = null;
        }

        if (!isHCUIMLocalInitialized && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
            loadHCUIMStatusIcon();
            isHCUIMLocalInitialized = true;
        }
    }

    @Provides
    HCUIMConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HCUIMConfig.class);
    }
}
