var websocket = null;

window.onload = function() {
    connect();
};

function connect() {
    var wsUri = 'ws://' + window.location.host + '/stats';
    var statusDot = document.getElementById("status-dot");
    var contentDiv = document.getElementById("stats-content");

    if ('WebSocket' in window) {
        websocket = new WebSocket(wsUri);
    } else {
        return;
    }

    websocket.onopen = function(event) {
        if(statusDot) statusDot.classList.add("connected");
        // REMOVIDO: contentDiv.innerHTML = "A aguardar..."
        // Agora não escrevemos nada aqui. O servidor vai mandar os dados
        // milissegundos depois, evitando o "piscar" da mensagem de espera.
    };

    websocket.onmessage = function(event) {
        try {
            var data = JSON.parse(event.data);
            if(contentDiv) renderDashboard(data, contentDiv);
        } catch (e) {
            console.error("Erro JSON", e);
        }
    };

    websocket.onclose = function(event) {
        if(statusDot) statusDot.classList.remove("connected");
        // Não apagamos o ecrã, mantemos os últimos dados visíveis
        setTimeout(connect, 2000);
    };
}

// ... (A função renderDashboard mantém-se igual à anterior) ...
function renderDashboard(data, container) {
    // ... Copie o resto da função renderDashboard da minha resposta anterior ...
    // ... Certifique-se que usa invertedIndexCount e incomingLinksCount ...

    let html = "";

    // --- 1. TOP PESQUISAS ---
    if (data.topSearchTerms && Object.keys(data.topSearchTerms).length > 0) {
        html += '<span class="stats-section-title">Top Pesquisas</span>';
        html += '<ul class="top-list">';

        let sortedSearch = Object.entries(data.topSearchTerms)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);

        sortedSearch.forEach(([term, count], index) => {
            html += `
                <li>
                    <span>${index + 1}. ${term}</span>
                    <span class="count-badge">${count}</span>
                </li>`;
        });
        html += '</ul>';
    }

    // --- 2. BARRELS ---
    if (data.barrelDetails && data.barrelDetails.length > 0) {
        html += '<span class="stats-section-title">Estado dos Barrels</span>';

        data.barrelDetails.forEach(barrel => {
            let isActive = (barrel.status === "Active");
            let statusLabel = isActive ? 'ATIVO' : 'OFFLINE';
            let cardClass = isActive ? '' : 'inactive';
            let badgeColor = isActive ? '#e6f4ea' : '#fce8e6';
            let textColor = isActive ? '#137333' : '#c5221f';

            html += `
                <div class="barrel-card ${cardClass}">
                    <div class="barrel-header">
                        <span>${barrel.name}</span>
                        <span style="font-size:10px; padding:2px 5px; background:${badgeColor}; color:${textColor}; border-radius:4px;">
                            ${statusLabel}
                        </span>
                    </div>
                    <div class="barrel-stats-grid">
                        <div>Palavras: <span class="stat-val">${barrel.invertedIndexCount || 0}</span></div>
                        <div>Links: <span class="stat-val">${barrel.incomingLinksCount || 0}</span></div>
                        <div style="grid-column: span 2; margin-top:4px; padding-top:4px; border-top:1px dashed #eee;">
                            Tempo Médio: <span style="color:#1a73e8; font-weight:bold;">
                                ${barrel.avgResponseTime ? barrel.avgResponseTime.toFixed(1) : 0}ms
                            </span>
                            <span style="font-size:9px;">(${barrel.requestCount || 0} reqs)</span>
                        </div>
                    </div>
                </div>`;
        });
    } else {
        html += '<div style="padding:10px; font-size:12px; color:#999; text-align:center;">Nenhum Barrel conectado.</div>';
    }

    container.innerHTML = html;
}