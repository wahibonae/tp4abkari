/* Copier le contenu d'un textarea dans le clipboard */
async function copyToClipboard(idTextArea) {
    var textArea = document.getElementById("form:" + idTextArea);
    if(textArea) {
        try {
            await navigator.clipboard.writeText(textArea.value);
        } catch (err) {
            console.error("Erreur lors de la copie : ", err)
        }
    }
    // Cet ancien code est deprecated :
    // textArea.select();
    // document.execCommand('copy');
}

/* Effacer la dernière question et la dernière réponse */
function toutEffacer() {
    document.getElementById("form:question").value = "";
    document.getElementById("form:reponse").value = "";
}

/* Fonction appelée quand un message arrive via WebSocket */
function socketListener(message, channel, event) {
    // Récupérer le textarea de la réponse
    var reponseTextArea = document.getElementById("form:reponse");

    if (reponseTextArea) {
        // Ajouter les nouveaux tokens à la réponse existante
        reponseTextArea.value += message;

        // Faire défiler automatiquement vers le bas
        reponseTextArea.scrollTop = reponseTextArea.scrollHeight;
    }
}