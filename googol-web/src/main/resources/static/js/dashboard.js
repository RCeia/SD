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
        if(contentDiv) contentDiv.innerHTML = "Browser não suportado.";
        return;
    }

    websocket.onopen = function(event) {
        if(statusDot) statusDot.classList.add("connected");
        // Não mostramos mensagem de espera, pois o servidor vai enviar
        // o estado dos barrels imediatamente.
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
        setTimeout(connect, 3000);
    };
}

function renderDashboard(data, container) {
    let html = "";

    // --- 1. TOP PESQUISAS ---
    html += '<span class="stats-section-title">Top Pesquisas</span>';
    html += '<ul class="top-list">';

    // Só desenha a lista SE houver pesquisas. Se não houver, mostra mensagem.
    if (data.topSearchTerms && Object.keys(data.topSearchTerms).length > 0) {
        let sortedSearch = Object.entries(data.topSearchTerms)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);

        sortedSearch.forEach(([term, count], index) => {
            html += `<li><span>${index + 1}. ${term}</span><span class="count-badge">${count}</span></li>`;
        });
    } else {
        // MENSAGEM QUANDO AINDA NÃO HÁ PESQUISAS
        html += '<li style="color:#999; font-style:italic; justify-content:center; padding:10px;">Sem pesquisas recentes</li>';
    }
    html += '</ul>';

    // --- 2. BARRELS (O que você quer ver imediatamente) ---
    html += '<span class="stats-section-title">Estado dos Barrels</span>';

    if (data.barrelDetails && data.barrelDetails.length > 0) {
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
        html += '<div style="padding:15px; font-size:12px; color:#999; text-align:center;">A aguardar conexão de Barrels...</div>';
    }

    container.innerHTML = html;
}