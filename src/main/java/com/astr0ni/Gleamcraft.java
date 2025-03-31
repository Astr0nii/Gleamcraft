package com.astr0ni;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.core.registries.Registries;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.Map;

@Mod(Gleamcraft.MOD_ID)
public class Gleamcraft {
    public static final String MOD_ID = "gleamcraft";
    public static final String MOD_NAME = "Gleamcraft";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    // CUSTOM TOOL MECHANIC
    public interface SunRepairingItem {
        // Map to track lights placed by each item in player inventory
        Map<UUID, Map<ItemStack, BlockPos>> PLAYER_ITEM_LIGHTS = new HashMap<>();
        default boolean isPlayerInSunlight(Player player) {
            Level level = player.level();
            BlockPos pos = player.blockPosition();

            // Check if it's daytime
            if (!level.isDay()) {
                return false;
            }

            // Check if player is in the Overworld
            if (level.dimension() != Level.OVERWORLD) {
                return false;
            }

            // Check if it's not raining
            if (level.isRaining() || level.isThundering()) {
                return false;
            }

            // Check if there's a clear path to the sky
            return level.canSeeSky(pos);
        }

        // Default method for repairing the item
        default void tryRepairInSun(ItemStack stack, Level level, Entity entity) {
            // Only proceed if this is on the server side, the entity is a player, and it's not a creative player
            if (level.isClientSide || !(entity instanceof Player) || ((Player) entity).getAbilities().instabuild) {
                return;
            }

            Player player = (Player) entity;

            // Check if conditions are met for sun repair
            if (isPlayerInSunlight(player)) {
                // Repair very slowly - once every 15 seconds (300 ticks)
                if (level.getGameTime() % 300 == 0) {
                    // Repair 1 durability point
                    if (stack.isDamaged()) {
                        stack.setDamageValue(stack.getDamageValue() - 1);
                    }
                }
            }
        }
        default void handleLightEmission(ItemStack stack, Level level, Entity entity) {
            if (level.isClientSide || !(entity instanceof Player player)) {
                return;
            }

            UUID playerId = player.getUUID();
            BlockPos playerPos = player.blockPosition();

            // Check if player is holding this item in either hand
            boolean isHoldingItem = player.getMainHandItem() == stack || player.getOffhandItem() == stack;

            Map<ItemStack, BlockPos> itemLights = PLAYER_ITEM_LIGHTS.computeIfAbsent(playerId, k -> new HashMap<>());

            if (isHoldingItem) {
                // Only place light if there isn't one already or the player moved
                BlockPos oldPos = itemLights.get(stack);
                if (oldPos == null || !oldPos.equals(playerPos)) {
                    // Remove old light if exists
                    if (oldPos != null) {
                        removeLight(level, oldPos);
                    }

                    // Place new light at player position if the block is replaceable
                    BlockState currentState = level.getBlockState(playerPos);
                    if (currentState.isAir() || currentState.canBeReplaced()) {
                        placeLight(level, playerPos);
                        itemLights.put(stack, playerPos);
                    }
                }
            } else {
                // Remove light if player is no longer holding the item
                BlockPos oldPos = itemLights.remove(stack);
                if (oldPos != null) {
                    removeLight(level, oldPos);
                }

                // Clean up player entry if no more lights are present
                if (itemLights.isEmpty()) {
                    PLAYER_ITEM_LIGHTS.remove(playerId);
                }
            }
        }

        private static void placeLight(Level level, BlockPos pos) {
            // Place a light block with level 10 light (adjust as needed)
            BlockState lightState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8);
            level.setBlock(pos, lightState, 3);
        }

        private static void removeLight(Level level, BlockPos pos) {
            // Only remove if it's our light block
            BlockState currentState = level.getBlockState(pos);
            if (currentState.getBlock() == Blocks.LIGHT) {
                level.removeBlock(pos, false);
            }
        }
    }

    // Custom sword class
    public static class GlimmerstoneSwordItem extends SwordItem implements SunRepairingItem {
        public GlimmerstoneSwordItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Properties properties) {
            super(toolMaterial, attackDamage, attackSpeed, properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            tryRepairInSun(stack, level, entity);
            handleLightEmission(stack, level, entity);
        }
    }

    // Custom axe class
    public static class GlimmerstoneAxeItem extends AxeItem implements SunRepairingItem {
        public GlimmerstoneAxeItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed, Properties properties) {
            super(toolMaterial, attackDamage, attackSpeed, properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            tryRepairInSun(stack, level, entity);
            handleLightEmission(stack, level, entity);
        }
    }

    // Custom pickaxe class
    public static class GlimmerstonePickaxeItem extends PickaxeItem implements SunRepairingItem {
        public GlimmerstonePickaxeItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Properties properties) {
            super(toolMaterial, attackDamage, attackSpeed, properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            tryRepairInSun(stack, level, entity);
            handleLightEmission(stack, level, entity);
        }
    }

    // Similarly for Shovel and Hoe
    public static class GlimmerstoneShovelItem extends ShovelItem implements SunRepairingItem {
        public GlimmerstoneShovelItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed, Properties properties) {
            super(toolMaterial, attackDamage, attackSpeed, properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            tryRepairInSun(stack, level, entity);
            handleLightEmission(stack, level, entity);
        }
    }

    public static class GlimmerstoneHoeItem extends HoeItem implements SunRepairingItem {
        public GlimmerstoneHoeItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed, Properties properties) {
            super(toolMaterial, attackDamage, attackSpeed, properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            tryRepairInSun(stack, level, entity);
            handleLightEmission(stack, level, entity);
        }
    }

    // DEFERRED REGISTERS
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);


    // TAGS
    // This tag will allow us to add these blocks to the incorrect tags that cannot mine them
    public static final TagKey<Block> NEEDS_GLIMMERSTONE_TOOL = TagKey.create(BuiltInRegistries.BLOCK.key(), ResourceLocation.fromNamespaceAndPath(MOD_ID, "needs_glimmerstone_tool"));

    // This tag will be passed into our material
    public static final TagKey<Block> INCORRECT_FOR_GLIMMERSTONE_TOOL = TagKey.create(BuiltInRegistries.BLOCK.key(), ResourceLocation.fromNamespaceAndPath(MOD_ID, "incorrect_for_glimmerstone_tool"));
    // Glimmerstone Crystal
    public static final TagKey<Item> GLIMMERSTONE_CRYSTALS = TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.fromNamespaceAndPath(MOD_ID, "glimmerstone_crystal"));


    // TOOL MATERIAL
    public static final ToolMaterial GLIMMERSTONE_MATERIAL = new ToolMaterial(
            // The tag that determines what blocks this material cannot break. See below for more information.
            INCORRECT_FOR_GLIMMERSTONE_TOOL,
            // Determines the durability of the material.
            600,
            // Determines the mining speed of the material. Unused by swords.
            6.5f,
            // Determines the attack damage bonus. Different tools use this differently. For example, swords do (getAttackDamageBonus() + 4) damage.
            2.25f,
            // Determines the enchantability of the material. This represents how good the enchantments on this tool will be.
            20,
            // The tag that determines what items can repair this material.
            GLIMMERSTONE_CRYSTALS
    );


    // BLOCKS

    // Glimmerstone ore
    public static final DeferredBlock<Block> GLIMMERSTONE_ORE = BLOCKS.register(
            "glimmerstone_ore",
            registryName -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, registryName))
                    .strength(3.0f, 3.0f)
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 3)
            ));
    // BlockItem for Glimmerstone Ore
    public static final DeferredItem<BlockItem> GLIMMERSTONE_ORE_ITEM = ITEMS.registerSimpleBlockItem("glimmerstone_ore", GLIMMERSTONE_ORE);
    // Deepslate variant
    public static final DeferredBlock<Block> DEEPSLATE_GLIMMERSTONE_ORE = BLOCKS.register(
            "deepslate_glimmerstone_ore",
            registryName -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, registryName))
                    .strength(4.5f, 3.0f)
                    .mapColor(MapColor.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 3)
            ));
    // BlockItem for Glimmerstone Ore
    public static final DeferredItem<BlockItem> DEEPSLATE_GLIMMERSTONE_ORE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_glimmerstone_ore", DEEPSLATE_GLIMMERSTONE_ORE);

    public static final DeferredBlock<Block> GLIMMERSTONE_CRYSTAL_BLOCK = BLOCKS.register(
            "glimmerstone_crystal_block",
            registryName -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, registryName))
                    .strength(2f, 6.5f)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 8)
            ));
    // BlockItem for Glimmerstone Crystal Block
    public static final DeferredItem<BlockItem> GLIMMERSTONE_CRYSTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("glimmerstone_crystal_block", GLIMMERSTONE_CRYSTAL_BLOCK);

    // BlockItem for Glimmerstone
    public static final DeferredBlock<Block> GLIMMERSTONE = BLOCKS.register(
            "glimmerstone",
            registryName -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, registryName))
                    .strength(1.25f, 1.25f)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.CALCITE)
                    .lightLevel(state -> 4)
            ));
    // BlockItem for Glimmerstone BlockItem
    public static final DeferredItem<BlockItem> GLIMMERSTONE_ITEM = ITEMS.registerSimpleBlockItem("glimmerstone", GLIMMERSTONE);


    // ITEMS
    // Glimmerstone crystal
    public static final DeferredItem<Item> GLIMMERSTONE_CRYSTAL = ITEMS.registerSimpleItem("glimmerstone_crystal");
    // Glimmerstone tools
    public static final DeferredItem<SwordItem> GLIMMERSTONE_SWORD = ITEMS.registerItem(
            "glimmerstone_sword",
            props -> new GlimmerstoneSwordItem(
                    GLIMMERSTONE_MATERIAL,
                    3,
                    -2.4f,
                    props
            )
    );
    public static final DeferredItem<AxeItem> GLIMMERSTONE_AXE = ITEMS.registerItem(
            "glimmerstone_axe",
            props -> new GlimmerstoneAxeItem(
                    GLIMMERSTONE_MATERIAL,
                    5.25f,
                    -3.05f,
                    props
            )
    );
    public static final DeferredItem<PickaxeItem> GLIMMERSTONE_PICKAXE = ITEMS.registerItem(
            "glimmerstone_pickaxe",
            props -> new GlimmerstonePickaxeItem(
                    GLIMMERSTONE_MATERIAL,
                    1,
                    -2.8f,
                    props
            )
    );
    public static final DeferredItem<ShovelItem> GLIMMERSTONE_SHOVEL = ITEMS.registerItem(
            "glimmerstone_shovel",
            props -> new GlimmerstoneShovelItem(
                    GLIMMERSTONE_MATERIAL,
                    1.5f,
                    -3f,
                    props
            )
    );
    public static final DeferredItem<HoeItem> GLIMMERSTONE_HOE = ITEMS.registerItem(
            "glimmerstone_hoe",
            props -> new GlimmerstoneHoeItem(
                    GLIMMERSTONE_MATERIAL,
                    -3f,
                    0f,
                    props
            )
    );


    // CREATIVE MODE TAB
    public static final Supplier<CreativeModeTab> GLEAMCRAFT_TAB = CREATIVE_MODE_TABS.register("gleamcraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gleamcraft"))
            .icon(() -> GLIMMERSTONE_CRYSTAL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GLIMMERSTONE_CRYSTAL.get()); // Add the glimmerstone crystal to our tab
                output.accept(GLIMMERSTONE.get()); // Add the glimmerstone to our tab
                output.accept(GLIMMERSTONE_ORE_ITEM.get()); // Add the glimmerstone ore to our tab
                output.accept(DEEPSLATE_GLIMMERSTONE_ORE_ITEM.get()); // Add the deepslate glimmerstone ore to our tab
                output.accept(GLIMMERSTONE_CRYSTAL_BLOCK_ITEM.get()); // Add the glimmerstone crystal block to our tab
                output.accept(GLIMMERSTONE_SWORD.get()); // Add the glimmerstone crystal sword to our tab
                output.accept(GLIMMERSTONE_PICKAXE.get()); // Add the glimmerstone crystal pickaxe to our tab
                output.accept(GLIMMERSTONE_AXE.get()); // Add the glimmerstone crystal axe to our tab
                output.accept(GLIMMERSTONE_SHOVEL.get()); // Add the glimmerstone crystal shovel to our tab
                output.accept(GLIMMERSTONE_HOE.get()); // Add the glimmerstone crystal hoe to our tab
            })
            .build());

    public Gleamcraft(IEventBus modEventBus) {

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }


    public static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}