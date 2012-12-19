package unused;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import net.minecraft.server.NBTBase;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.NBTTagList;
import org.bukkit.craftbukkit.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ItemSerialization {
	public static String toBase64(Inventory inventory) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		DataOutputStream dataOutput = new DataOutputStream(outputStream);
		NBTTagList itemList = new NBTTagList();

		// Save every element in the list
		for (int i = 0; i < inventory.getSize(); i++) {
			NBTTagCompound outputObject = new NBTTagCompound();
			CraftItemStack craft = getCraftVersion(inventory.getItem(i));

			// Convert the item stack to a NBT compound
			if (craft != null)
				craft.getHandle().save(outputObject);
			itemList.add(outputObject);
		}

		// Now save the list
		NBTBase.a(itemList, dataOutput);

		// Serialize that array
		return new BigInteger(1, outputStream.toByteArray()).toString(32);
	}

	public static Inventory fromBase64(String data) {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(
				new BigInteger(data, 32).toByteArray());
		NBTTagList itemList = (NBTTagList) NBTBase.b(new DataInputStream(
				inputStream));
		Inventory inventory = new CraftInventoryCustom(null, itemList.size());

		for (int i = 0; i < itemList.size(); i++) {
			NBTTagCompound inputObject = (NBTTagCompound) itemList.get(i);

			// IsEmpty
			if (!inputObject.d()) {
				inventory.setItem(i, new CraftItemStack(
						net.minecraft.server.ItemStack.a(inputObject)));
			}
		}

		// Serialize that array
		return inventory;
	}

	public static Inventory getArmorInventory(PlayerInventory inventory) {
		ItemStack[] armor = inventory.getArmorContents();
		CraftInventoryCustom storage = new CraftInventoryCustom(null,
				armor.length);

		for (int i = 0; i < armor.length; i++)
			storage.setItem(i, armor[i]);
		return storage;
	}

	private static CraftItemStack getCraftVersion(ItemStack stack) {
		if (stack instanceof CraftItemStack)
			return (CraftItemStack) stack;
		else if (stack != null)
			return new CraftItemStack(stack);
		else
			return null;
	}
}