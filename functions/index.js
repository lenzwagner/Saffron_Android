/**
 * Zephron push notifications.
 *
 * Triggers whenever a message lands in a user's inbox
 * (users/{uid}/inbox/{msgId}) — friend request, accept, decline or match —
 * and sends an FCM push to that user's devices so notifications arrive even
 * when the app is closed. The text is localized using the recipient's saved
 * language preference (de / en).
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const CHANNEL_ID = "zephron_notifications";

function buildText(type, lang, fromName, recipeTitle) {
  const de = {
    friend_request: ["Neue Freundschaftsanfrage", `${fromName} möchte sich mit dir verbinden.`],
    request_accepted: ["Anfrage angenommen ✅", `${fromName} hat deine Freundschaftsanfrage angenommen.`],
    request_declined: ["Anfrage abgelehnt", `${fromName} hat deine Freundschaftsanfrage abgelehnt.`],
    match: ["Match! ❤️", `${fromName} und du wollt beide ${recipeTitle} kochen!`],
  };
  const en = {
    friend_request: ["New friend request", `${fromName} wants to connect with you.`],
    request_accepted: ["Request accepted ✅", `${fromName} accepted your friend request.`],
    request_declined: ["Request declined", `${fromName} declined your friend request.`],
    match: ["Match! ❤️", `${fromName} and you both want to cook ${recipeTitle}!`],
  };
  const table = lang === "en" ? en : de;
  return table[type] || null;
}

exports.notifyOnInbox = onDocumentCreated("users/{uid}/inbox/{msgId}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  const msg = snap.data() || {};
  const uid = event.params.uid; // recipient = inbox owner
  const type = msg.type;
  const fromName = msg.fromName || "Jemand";
  const recipeTitle = msg.recipeTitle || "ein Rezept";

  const db = getFirestore();

  // Recipient's language preference (defaults to German)
  let lang = "de";
  try {
    const settings = await db.doc(`users/${uid}/config/settings`).get();
    if (settings.exists && settings.data().language) lang = settings.data().language;
  } catch (e) {
    console.error("Failed to read language", e);
  }

  const text = buildText(type, lang, fromName, recipeTitle);
  if (!text) return; // unknown message type → no push
  const [title, body] = text;

  // Collect the recipient's device tokens
  const tokensSnap = await db.collection(`users/${uid}/fcmTokens`).get();
  const tokens = tokensSnap.docs.map((d) => d.id);
  if (tokens.length === 0) return;

  const message = {
    tokens,
    notification: { title, body },
    android: {
      priority: "high",
      notification: { channelId: CHANNEL_ID },
    },
  };

  const resp = await getMessaging().sendEachForMulticast(message);

  // Remove tokens that are no longer valid
  const invalid = [];
  resp.responses.forEach((r, i) => {
    if (!r.success) {
      const code = (r.error && r.error.code) || "";
      if (
        code.includes("registration-token-not-registered") ||
        code.includes("invalid-argument")
      ) {
        invalid.push(tokens[i]);
      }
    }
  });
  await Promise.all(
    invalid.map((t) => db.doc(`users/${uid}/fcmTokens/${t}`).delete())
  );

  console.log(`Sent ${type} push to ${uid}: ${resp.successCount}/${tokens.length} ok`);
});
