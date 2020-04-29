# Runelite Hardcore Ultimate Ironman plugin
This plugin should show a specific ironman icon replacement when a player is a HCUIM, meaning it is a UIM that is also a HC.
For a player to be a HCUIM, using this Runelite plugin, the player must adhere the following rules:
1. The player is enrolled as an Ultimate Ironman (from tutorial island)
2. The player needs to have 100% of its XP gained on RuneLite (except for tutorial island) (this is necessary as the player can otherwise cheat on alternative clients)
3. The player has not died, where the same rules for dying are applied as for Hardcore Ironman mode

## How to start a HCUIM
1. Install RuneLite
2. Enable this plugin
3. Create a new account and go through tutorial island, *pick Ultimate Iron Man*
4. Play the game! Make sure you *only use the RuneLite client* throughout your entire play time of your HCUIM. If this plugin sees that your
 account obtained XP outside of RuneLite, it rejects the Hardcore status and makes you a regular UIM.
 
## Testing/development to-do:
* There needs to be more testing of "hardcore-safe deaths" (deaths where you do not lose your hardcore status). There may be some deaths that should be safe but are unsafe in this code, and vice versa. See HCUIMPlugin.java to inspect how the death mechanics are currently implemented.
