package com.aizistral.nochatreports.common.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;

import com.aizistral.nochatreports.common.NCRCore;
import com.aizistral.nochatreports.common.config.NCRConfig;
import com.aizistral.nochatreports.common.core.EncryptionUtil;

import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

@Mixin(ChatComponent.class)
public class MixinChatComponent {
	private static final GuiMessageTag.Icon ENCRYPTED_ICON = GuiMessageTag.Icon.valueOf("CHAT_NCR_ENCRYPTED");
	private boolean lastMessageEncrypted;
	private Component lastMessageOriginal;

	@ModifyVariable(method = "addRecentChat", at = @At("HEAD"), argsOnly = true)
	private String onAddRecentChat(String message) {
		if (NCRConfig.getEncryption().isEnabledAndValid())
			return NCRConfig.getEncryption().getLastMessage();
		else
			return message;
	}

	@ModifyVariable(index = -1, method = "addMessage(Lnet/minecraft/network/chat/Component;"
			+ "Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;"
					+ "logChatMessage(Lnet/minecraft/client/GuiMessage;)V", ordinal = 0, shift = Shift.BEFORE))
	private GuiMessage modifyGUIMessage(GuiMessage msg) {
		if (NCRConfig.getCommon().enableDebugLog()) {
			NCRCore.LOGGER.info("Adding chat message, structure: " +
					Component.Serializer.toJson(msg.content(),  RegistryAccess.EMPTY));
		}

		var decrypted = EncryptionUtil.tryDecrypt(msg.content());

		decrypted.ifPresentOrElse(component -> {
			this.lastMessageOriginal = EncryptionUtil.recreate(msg.content());
			this.lastMessageEncrypted = true;
		}, () -> this.lastMessageEncrypted = false);

		if (this.lastMessageEncrypted) {
			this.lastMessageEncrypted = false;

			Component decryptedComponent = decrypted.get();
			GuiMessageTag newTag = msg.tag();

			if (NCRConfig.getEncryption().showEncryptionIndicators()) {
				Component tooltip = Component.empty().append(Component.translatable("tag.nochatreports.encrypted",
						Component.literal(NCRConfig.getEncryption().getAlgorithm().getName())
						.withStyle(ChatFormatting.BOLD))).append(CommonComponents.NEW_LINE).append(
								Component.translatable("tag.nochatreports.encrypted_original",
										this.lastMessageOriginal));

				newTag = new GuiMessageTag(0x8B3EC7, ENCRYPTED_ICON, tooltip, "Encrypted");
			}

			return new GuiMessage(msg.addedTime(), decryptedComponent, msg.signature(), newTag);
		} else
			return msg;
	}

}
