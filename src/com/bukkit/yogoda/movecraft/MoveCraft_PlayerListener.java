package com.bukkit.yogoda.movecraft;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;

import org.bukkit.event.player.PlayerChatEvent;

import org.bukkit.event.player.*;

public class MoveCraft_PlayerListener extends PlayerListener {
	private final MoveCraft plugin;

	public MoveCraft_PlayerListener(MoveCraft instance) {
		plugin = instance;
	}

	@Override
	public void onPlayerQuit(PlayerEvent event) {
		Player player = event.getPlayer();

		Craft craft = Craft.getCraft(player);

		if (craft != null) {
			Craft.removeCraft(craft);
		}
	}

	@Override
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();

		Craft craft = Craft.getCraft(player);

		if (craft != null) {
			craft.setSpeed(1);

			if (craft.isOnBoard && !craft.isOnCraft(player, false)) {
				player.sendMessage(ChatColor.YELLOW + "You get off the " + craft.name);
				player.sendMessage(ChatColor.GRAY + "Type /" + craft.name
						+ " remote for remote control");
				player.sendMessage(ChatColor.YELLOW + "If you don't, you'll lose control in 15 seconds.");
				craft.isOnBoard = false;
				craft.haveControl = false;
				
				int CraftReleaseDelay = 15;
				try {
				CraftReleaseDelay = Integer.parseInt(plugin.configFile.ConfigSettings.get("CraftReleaseDelay"));
				}
				catch (NumberFormatException ex) {
					System.out.println("ERROR with playermove. Could not parse " + plugin.configFile.ConfigSettings.get("CraftReleaseDelay"));
				}
				if(CraftReleaseDelay != 0)
					craft.timer = new MoveCraft_Timer(CraftReleaseDelay, craft, "abandonCheck", false);
			} else if (!craft.isOnBoard && craft.isOnCraft(player, false)) {
				player.sendMessage(ChatColor.YELLOW + "Welcome on board");
				craft.isOnBoard = true;
				craft.haveControl = true;
				if(craft.timer != null)
					craft.timer.Destroy();
			}
		}
	}

	public void onPlayerItem(PlayerItemEvent event) {
		Player player = event.getPlayer();

		Craft craft = Craft.getCraft(player);

		if (craft != null) {

			if (craft.blockPlaced) {
				craft.blockPlaced = false;
				return;
			}

			if (craft.blockCount <= 0) {

				plugin.releaseCraft(player, craft);
				return;
			}

			ItemStack pItem = player.getItemInHand();
			int item = pItem.getTypeId();

			plugin.DebugMessage(player.getName() + " used item " + Integer.toString(item));

			// the craft won't budge if you have any tool in the hand
			if (!craft.haveControl ||
			/*
			 * item == 256 || item == 257 || item == 258 ||
			 * 
			 * item == 269 || item == 270 || item == 271 ||
			 * 
			 * item == 273 || item == 274 || item == 275 ||
			 * 
			 * item == 277 || item == 278 || item == 279 ||
			 * 
			 * item == 284 || item == 285 || item == 286 || (item >= 290 && item
			 * <= 294) ||
			 */
			item == 336 // the brick, compatibility with PushBlocks
			) {
				if( (item == craft.type.remoteControllerItem || 
						item == Integer.parseInt(plugin.configFile.ConfigSettings.get("UniversalRemoteId")))
					&& !craft.isOnCraft(player, true)
					&& PermissionInterface.CheckPermission(player, "remote")) {
						if (craft.haveControl) {
							player.sendMessage(ChatColor.YELLOW + "You switch off the remote controller");
						} else {
							if(craft.timer != null)
								craft.timer.Destroy();
							player.sendMessage(ChatColor.YELLOW + "You switch on the remote controller");
						}
						craft.haveControl = !craft.haveControl;
				}					
				else return;
			}

			// minimum time between 2 swings
			if (System.currentTimeMillis() - craft.lastMove < 0.2 * 1000)
				return;

			float rotation = (float) Math.PI * player.getLocation().getYaw() / 180f;

			// Not really sure what the N stands for...
			float nx = -(float) Math.sin(rotation);
			float nz = (float) Math.cos(rotation);

			int dx = (Math.abs(nx) >= 0.5 ? 1 : 0) * (int) Math.signum(nx);
			int dz = (Math.abs(nz) > 0.5 ? 1 : 0) * (int) Math.signum(nz);

			int dy = 0;
			
			/* Rotation code */
			//double angle = 180f * (Math.atan2(dz, dx) - Math.atan2(craft.dirZ, craft.dirX)) / (float)Math.PI;
			//int r = ((int)Math.round(angle) + 360) % 360;
            //if(r == 180)
                //r = 0;

			// we are on a flying object, handle height change
			if (craft.type.canFly || craft.type.canDive || craft.type.canDig) {

				float p = player.getLocation().getPitch();

				dy = -(Math.abs(player.getLocation().getPitch()) >= 25 ? 1 : 0)
						* (int) Math.signum(p);

				// move straight up or straight down
				if (Math.abs(player.getLocation().getPitch()) >= 75) {
					dx = 0;
					dz = 0;
				}
			}

			craft.calculatedMove(dx, dy, dz);
		}
	}

	public void onPlayerCommand(PlayerChatEvent event) {
		Player player = event.getPlayer();
		String[] split = event.getMessage().split(" ");
		split[0] = split[0].substring(1);
		
		String command = "";
		for(int i = 0; i < split.length; i++) {
			command += "." + split[i];
		}
		if (PermissionInterface.CheckPermission(player, command.substring(1)) == false) {
			return;
		}
		
		if (split[0].equalsIgnoreCase("hyperspace")) {
			Craft craft = Craft.getCraft(player);
			
			if(craft == null) {
				player.kickPlayer("You tried to go into hyperspace while not controlling a craft while I'm in a bad mood.");
				return;
			}
			
			if(!craft.inHyperSpace)
				Craft_Hyperspace.enterHyperSpace(craft);
			else
				Craft_Hyperspace.exitHyperSpace(craft);
		} else if (split[0].equalsIgnoreCase("turn")) {
			Craft craft = Craft.getCraft(player);
			CraftRotator cr = new CraftRotator(craft, plugin);
			
			if(split[1].equalsIgnoreCase("right"))
				cr.move(0, 0, 0, 90);
			else
				cr.move(0, 0, 0, -90);
		} else if (split[0].equalsIgnoreCase("warpdrive")) {
				if(split.length == 1) {
					List<World> worlds = plugin.getServer().getWorlds();
					for(World world : worlds)
						player.sendMessage(world.getName() + " : " + world.getId());
				} else {
					Craft craft = Craft.getCraft(player);
					try
					{
						int WorldNum = Integer.parseInt(split[1]);
						World targetWorld = plugin.getServer().getWorlds().get(WorldNum);
						craft.WarpToWorld(targetWorld);						
					}
					catch (NumberFormatException ex)
					{
						World targetWorld = plugin.getServer().getWorld(split[1]); 
						if(targetWorld != null) {
							craft.WarpToWorld(targetWorld);
						}
						else {
							if(split[2].equalsIgnoreCase("nether"))
								plugin.getServer().createWorld(split[1], Environment.NETHER);
							else
								plugin.getServer().createWorld(split[1], Environment.NORMAL);
						}
					}
				}
			} else
		if (split[0].equalsIgnoreCase("movecraft")) {
			if (split.length >= 2) {
				if (split[1].equalsIgnoreCase("types")) {

					for (CraftType craftType : CraftType.craftTypes) {
						
						if(craftType.canUse(player))
							player.sendMessage(ChatColor.GREEN + craftType.name + ChatColor.YELLOW
								+ craftType.minBlocks + "-"
								+ craftType.maxBlocks + " blocks" + " speed : "
								+ craftType.maxSpeed);
					}
				} else if (split[1].equalsIgnoreCase("list")) {
					// list all craft currently controlled by a player

					if (Craft.craftList.isEmpty()) {
						player.sendMessage(ChatColor.YELLOW + "no player controlled craft");
						// return true;
					}

					for (Craft craft : Craft.craftList) {

						player.sendMessage(ChatColor.YELLOW + craft.name
								+ " controlled by " + craft.player.getName()
								+ " : " + craft.blockCount + " blocks");
					}
				} else if (split[1].equalsIgnoreCase("reload")) {
					//plugin.loadProperties();
					plugin.loadProperties();
					player.sendMessage(ChatColor.YELLOW + "configuration reloaded");
				} else if (split[1].equalsIgnoreCase("debug")) {
					plugin.ToggleDebug();
				}
				else if (split[1].equalsIgnoreCase("config")) {
					plugin.configFile.ListSettings(player);
				}
			}
			else {
				player.sendMessage(ChatColor.WHITE + "MoveCraft v" + MoveCraft.version + " commands :");
				player.sendMessage(ChatColor.YELLOW + "/movecraft types "
						+ " : " + ChatColor.WHITE + "list the types of craft available");
				player.sendMessage(ChatColor.YELLOW + "/movecraft list : " + ChatColor.WHITE + "list the current player controled craft");
				player.sendMessage(ChatColor.YELLOW + "/movecraft reload : " + ChatColor.WHITE + "reload config files");
				player.sendMessage(ChatColor.YELLOW + "/[craft type] "
						+ " : " + ChatColor.WHITE + "commands specific to the craft type");
			}
		} else if (split[0].equalsIgnoreCase("release")) {
			plugin.releaseCraft(player, Craft.getCraft(player));
		} else {
			String craftName = split[0];

			CraftType craftType = CraftType.getCraftType(craftName);

			if (craftType != null) {
				processCommand(craftType, player, split);
			}
		}

		return;
	}

	public boolean processCommand(CraftType craftType, Player player, String[] split) {

		Craft craft = Craft.getCraft(player);

		if (split.length >= 2) {

			if (split[1].equalsIgnoreCase(craftType.driveCommand)) {
				
				if(!craftType.canUse(player)){
					player.sendMessage(ChatColor.RED + "You are not allowed to use this type of craft");
					return false;
				}

				// try to detect and create the craft
				// use the block the player is standing on
				plugin.createCraft(player, craftType,
						(int) Math.floor(player.getLocation().getX()),
						(int) Math.floor(player.getLocation().getY() - 1),
						(int) Math.floor(player.getLocation().getZ()), null);

				return true;
			} else if (split[1].equalsIgnoreCase("setspeed")) {

				if (craft == null) {
					player.sendMessage(ChatColor.YELLOW + "You don't have any "
							+ craftType.name);
					return true;
				}

				int speed = Math.abs(Integer.parseInt(split[2]));

				if (speed < 1 || speed > craftType.maxSpeed) {
					player.sendMessage(ChatColor.YELLOW + "Allowed speed between 1 and "
							+ craftType.maxSpeed);
					return true;
				}

				craft.setSpeed(speed);
				player.sendMessage(ChatColor.YELLOW + craft.name + "'s speed set to "
						+ craft.speed);

				return true;
			}
			if (split[1].equalsIgnoreCase("setname")) {

				if (craft == null) {
					player.sendMessage(ChatColor.YELLOW + "You don't have any "
							+ craftType.name);
					return true;
				}

				craft.name = split[2];
				player.sendMessage(ChatColor.YELLOW + craft.name + "'s name set to "
						+ craft.name);
				return true;
			} else if (split[1].equalsIgnoreCase("size")) {

				if (craft == null) {
					player.sendMessage(ChatColor.YELLOW + "You don't have any "
							+ craftType.name);
					return true;
				}

				player.sendMessage(ChatColor.YELLOW + "The " + craft.name + " is built with "
						+ craft.blockCount + " blocks");
				return true;
			} else if (split[1].equalsIgnoreCase("remote")) {

				if (craft == null) {
					player.sendMessage(ChatColor.YELLOW + "You don't have any "
							+ craftType.name);
					return true;
				}

				if (craft.isOnCraft(player, true)) {
					player.sendMessage(ChatColor.YELLOW + "You are on the " + craftType.name
							+ ", remote control not possible");
				} else {
					if (craft.haveControl) {
						player.sendMessage(ChatColor.YELLOW + "You switch off the remote controller");
					} else {
						if(craft.timer != null)
							craft.timer.Destroy();
						player.sendMessage(ChatColor.YELLOW + "You switch on the remote controller");
					}

					craft.haveControl = !craft.haveControl;
				}

				return true;
			} else if (split[1].equalsIgnoreCase("release")) {

				plugin.releaseCraft(player, craft);
				return true;

			} else if (split[1].equalsIgnoreCase("info")) {

				player.sendMessage(ChatColor.WHITE + craftType.name);
				if(craft != null)
					player.sendMessage(ChatColor.YELLOW +
							Integer.toString(craftType.minBlocks) + "-" + craftType.maxBlocks + " blocks." + 
							" (Using " + craft.blockCount + ".)");
				else
					player.sendMessage(ChatColor.YELLOW + 
							Integer.toString(craftType.minBlocks) + "-" + craftType.maxBlocks + " blocks.");
				player.sendMessage(ChatColor.YELLOW +"Max speed: " + craftType.maxSpeed);

				if (plugin.DebugMode)
					player.sendMessage(ChatColor.YELLOW + Integer.toString(craft.dataBlocks.size()) + " data Blocks, " + 
							craft.complexBlocks.size() + " complex Blocks, " + 
							craft.engineBlocks.size() + " engine Blocks," + 
							craft.digBlockCount + " drill bits.");
				
				String canDo = ChatColor.YELLOW + craftType.name + "s can ";

				if (craftType.canFly)
					canDo += "fly, ";

				if (craftType.canDive)
					canDo += "dive, ";
				
				if(craftType.canDig)
					canDo += "dig, ";

				if (craftType.canNavigate)
					canDo += " navigate on both water and lava, ";
				
				player.sendMessage(canDo);

				if (craftType.flyBlockType != 0) {
					int flyBlocksNeeded = (int)Math.floor((craft.blockCount - craft.flyBlockCount) * ((float)craft.type.flyBlockPercent * 0.01) / (1 - ((float)craft.type.flyBlockPercent * 0.01)));

					if(flyBlocksNeeded < 1)
						flyBlocksNeeded = 1;

					player.sendMessage(ChatColor.YELLOW + "Flight requirement: "
							+ craftType.flyBlockPercent + "%" + " of "
							+ BlocksInfo.getName(craft.type.flyBlockType)
							+ "(" + flyBlocksNeeded + ")");
				}

				return true;

			} else if (split[1].equalsIgnoreCase("addwaypoint")) {
				//if(split[2].equalsIgnoreCase("absolute"))
				if(split[2].equalsIgnoreCase("relative")) {
					Location newLoc = craft.WayPoints.get(craft.WayPoints.size() - 1);
					if(split[3] != "0")
						newLoc.setX(newLoc.getX() + Integer.parseInt(split[3]));
					else if(split[4] != "0")
						newLoc.setY(newLoc.getY() + Integer.parseInt(split[4]));
					else if(split[5] != "0")
						newLoc.setZ(newLoc.getZ() + Integer.parseInt(split[5]));
					
					craft.addWayPoint(newLoc);
				} else
					craft.addWayPoint(player.getLocation());
				
				player.sendMessage("Added waypoint...");
			} else if (split[1].equalsIgnoreCase("autotravel")) {
				if(split[2] == "true")
					new MoveCraft_Timer(0, craft, "automove", true);
				else
					new MoveCraft_Timer(0, craft, "automove", false);
			}
		}

		player.sendMessage(ChatColor.WHITE + "MoveCraft v" + MoveCraft.version + " commands :");
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ craftType.driveCommand + " : " + ChatColor.WHITE + "" + " "
				+ craftType.driveCommand + " the " + craftType.name);
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ "release : " + ChatColor.WHITE + "release the " + craftType.name);
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ "remote : " + ChatColor.WHITE + "remote control of the " + craftType.name);
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ "size : " + ChatColor.WHITE + "the size of the " + craftType.name + " in block");
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ "setname : " + ChatColor.WHITE + "set the " + craftType.name + "'s name");
		player.sendMessage(ChatColor.YELLOW + "/" + craftType.name + " "
				+ "info : " + ChatColor.WHITE + "displays informations about the " + craftType.name);

		return true;
	}
}
