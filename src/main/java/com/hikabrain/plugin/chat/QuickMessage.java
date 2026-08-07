package com.hikabrain.plugin.chat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Les 4 messages rapides envoyés dans le chat de l'arène en cliquant sur un bloc de
 * couleur dans la hotbar pendant le temps d'attente après un point (et à la victoire
 * finale) — pas le temps d'écrire dans le chat entre deux rounds.
 *
 * Chaque message est traduit automatiquement dans la langue de CHAQUE destinataire
 * (déterminée via {@link Player#locale()}, réglée automatiquement par le client selon
 * la langue configurée dans les options du jeu du joueur — aucune saisie manuelle
 * n'est nécessaire). Un adversaire dont le jeu est en anglais verra donc le message
 * en anglais, même si l'auteur a le jeu en français, et inversement.
 *
 * Le texte français ci-dessous fait office de texte "source" ET de valeur par défaut
 * si aucune traduction n'est disponible pour la langue du destinataire (dans ce cas,
 * on retombe sur l'anglais plutôt que sur le français, l'anglais étant plus
 * universellement compris qu'une langue tierce).
 */
public enum QuickMessage {

    WELL_PLAYED(Material.GREEN_CONCRETE, ChatColor.GREEN, 0, "Bien joué !", Map.ofEntries(
            Map.entry("en", "Well played!"),
            Map.entry("es", "¡Bien jugado!"),
            Map.entry("de", "Gut gespielt!"),
            Map.entry("pt", "Bem jogado!"),
            Map.entry("ru", "Отлично сыграно!"),
            Map.entry("it", "Ben giocato!"),
            Map.entry("nl", "Goed gespeeld!"),
            Map.entry("pl", "Dobra gra!"),
            Map.entry("tr", "İyi oynadın!"),
            Map.entry("zh", "打得好！"),
            Map.entry("ja", "ナイスプレー！"),
            Map.entry("ko", "잘했어요!")
    )),

    TOO_STRONG(Material.PURPLE_CONCRETE, ChatColor.DARK_PURPLE, 1, "Tu es trop fort !", Map.ofEntries(
            Map.entry("en", "You're too strong!"),
            Map.entry("es", "¡Eres demasiado fuerte!"),
            Map.entry("de", "Du bist zu stark!"),
            Map.entry("pt", "Você é forte demais!"),
            Map.entry("ru", "Ты слишком силён!"),
            Map.entry("it", "Sei troppo forte!"),
            Map.entry("nl", "Je bent te sterk!"),
            Map.entry("pl", "Jesteś zbyt silny!"),
            Map.entry("tr", "Çok güçlüsün!"),
            Map.entry("zh", "你太强了！"),
            Map.entry("ja", "強すぎる！"),
            Map.entry("ko", "너무 강해요!")
    )),

    REVENGE(Material.ORANGE_CONCRETE, ChatColor.GOLD, 2, "J'aurais ma revanche !", Map.ofEntries(
            Map.entry("en", "I'll have my revenge!"),
            Map.entry("es", "¡Tendré mi revancha!"),
            Map.entry("de", "Ich werde mich rächen!"),
            Map.entry("pt", "Terei minha revanche!"),
            Map.entry("ru", "Я отомщу!"),
            Map.entry("it", "Avrò la mia rivincita!"),
            Map.entry("nl", "Ik krijg mijn revanche!"),
            Map.entry("pl", "Wezmę odwet!"),
            Map.entry("tr", "İntikamımı alacağım!"),
            Map.entry("zh", "我会报仇的！"),
            Map.entry("ja", "リベンジしてやる！"),
            Map.entry("ko", "복수하고 말겠어!")
    )),

    NOT_SCARED(Material.BLUE_CONCRETE, ChatColor.BLUE, 3, "Même pas peur !", Map.ofEntries(
            Map.entry("en", "Not even scared!"),
            Map.entry("es", "¡Ni siquiera tengo miedo!"),
            Map.entry("de", "Nicht mal Angst!"),
            Map.entry("pt", "Nem um pouco de medo!"),
            Map.entry("ru", "Даже не страшно!"),
            Map.entry("it", "Non ho neanche paura!"),
            Map.entry("nl", "Niet eens bang!"),
            Map.entry("pl", "Wcale się nie boję!"),
            Map.entry("tr", "Hiç korkmadım!"),
            Map.entry("zh", "一点都不怕！"),
            Map.entry("ja", "全然怖くない！"),
            Map.entry("ko", "하나도 안 무서워!")
    ));

    private final Material material;
    private final ChatColor color;
    private final int hotbarSlot;
    private final String frenchText;
    private final Map<String, String> translations;

    QuickMessage(Material material, ChatColor color, int hotbarSlot, String frenchText, Map<String, String> translations) {
        this.material = material;
        this.color = color;
        this.hotbarSlot = hotbarSlot;
        this.frenchText = frenchText;
        this.translations = translations;
    }

    public Material getMaterial() {
        return material;
    }

    public ChatColor getColor() {
        return color;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    /**
     * Renvoie le texte de ce message dans la langue du destinataire donné (détectée via
     * {@link Player#locale()}, réglée automatiquement par son client). Retombe sur
     * l'anglais si aucune traduction n'existe pour sa langue précise, ou sur le français
     * si le destinataire est lui-même francophone (ou en dernier recours, si même
     * l'anglais n'était pas disponible, ce qui n'arrive jamais ici).
     */
    public String getTextFor(Player recipient) {
        String language = recipient.locale().getLanguage(); // ex: "fr", "en", "de"...
        if ("fr".equalsIgnoreCase(language)) {
            return frenchText;
        }
        String translated = translations.get(language.toLowerCase());
        if (translated != null) {
            return translated;
        }
        // Pas de traduction pour cette langue précise : l'anglais reste plus
        // universellement compris que le français pour un joueur non-francophone.
        return translations.getOrDefault("en", frenchText);
    }

    /**
     * Crée l'ItemStack représentant ce message (bloc de couleur nommé), à placer dans
     * la hotbar pendant le temps d'attente (voir GameManager#giveQuickChatItems).
     *
     * Le NOM affiché et le texte d'aide (lore) sont traduits dans la langue du joueur qui
     * va VOIR cet item dans SON PROPRE inventaire (même logique que {@link #getTextFor}) :
     * chaque joueur voit donc "Bien joué !" écrit dans sa propre langue directement sur le
     * bloc, avant même de cliquer dessus, et sait ainsi ce que chaque message veut dire.
     */
    public ItemStack createItem(Player viewer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + "" + ChatColor.BOLD + getTextFor(viewer));
            meta.setLore(java.util.List.of(ChatColor.GRAY + getHintFor(viewer)));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Traductions du petit texte d'aide affiché sous chaque bloc (identique pour les 4 messages). */
    private static final Map<String, String> HINT_TRANSLATIONS = Map.ofEntries(
            Map.entry("en", "Click to send this message in the arena chat!"),
            Map.entry("es", "¡Haz clic para enviar este mensaje en el chat de la arena!"),
            Map.entry("de", "Klicke, um diese Nachricht im Arena-Chat zu senden!"),
            Map.entry("pt", "Clique para enviar esta mensagem no chat da arena!"),
            Map.entry("ru", "Нажми, чтобы отправить это сообщение в чат арены!"),
            Map.entry("it", "Clicca per inviare questo messaggio nella chat dell'arena!"),
            Map.entry("nl", "Klik om dit bericht in de arena-chat te sturen!"),
            Map.entry("pl", "Kliknij, aby wysłać tę wiadomość na czacie areny!"),
            Map.entry("tr", "Bu mesajı arena sohbetine göndermek için tıkla!"),
            Map.entry("zh", "点击将此消息发送到竞技场聊天！"),
            Map.entry("ja", "クリックしてこのメッセージをアリーナチャットに送信！"),
            Map.entry("ko", "클릭하면 이 메시지가 아레나 채팅에 전송됩니다!")
    );
    private static final String HINT_FRENCH = "Clique pour envoyer ce message dans le chat de l'arène !";

    private static String getHintFor(Player viewer) {
        String language = viewer.locale().getLanguage();
        if ("fr".equalsIgnoreCase(language)) return HINT_FRENCH;
        return HINT_TRANSLATIONS.getOrDefault(language.toLowerCase(), HINT_TRANSLATIONS.get("en"));
    }

    /**
     * Identifie quel message rapide correspond à cet ItemStack (via son Material — ces
     * 4 blocs ne sont utilisés nulle part ailleurs dans le kit HikaBrain, une simple
     * comparaison de Material suffit donc, pas besoin de tag NBT). Renvoie null si
     * l'item donné n'est pas un bloc de message rapide (ou est null/AIR).
     */
    public static QuickMessage fromItem(ItemStack item) {
        if (item == null) return null;
        for (QuickMessage message : values()) {
            if (message.material == item.getType()) return message;
        }
        return null;
    }
}
