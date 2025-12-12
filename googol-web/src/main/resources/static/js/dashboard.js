var websocket = null;

window.onload = function() {
    connect();
};

function connect() {
    // --- ALTERAÇÃO AQUI ---
    // Verifica se a página está a ser carregada por HTTPS
    // Se for HTTPS, usa wss:// (WebSocket Secure). Se for HTTP, usa ws://
    var protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    var wsUri = protocol + window.location.host + '/stats';

    var statusDot = document.getElementById("status-dot");
    var contentDiv = document.getElementById("stats-content");

    // Verifica se o browser suporta WebSockets
    if ('WebSocket' in window) {
        websocket = new WebSocket(wsUri);
    } else {
        if(contentDiv) contentDiv.innerHTML = "Browser não suportado.";
        return;
    }

    websocket.onopen = function(event) {
        // Conexão estabelecida: muda a cor da bolinha para verde
        if(statusDot) statusDot.classList.add("connected");
        console.log("WebSocket conectado via " + protocol);
    };

    websocket.onmessage = function(event) {
        try {
            // Recebe os dados do servidor e atualiza o HTML
            var data = JSON.parse(event.data);
            if(contentDiv) renderDashboard(data, contentDiv);
        } catch (e) {
            console.error("Erro ao processar JSON", e);
        }
    };

    websocket.onclose = function(event) {
        // Conexão perdida: muda a cor da bolinha para vermelho
        if(statusDot) statusDot.classList.remove("connected");

        // Tenta reconectar automaticamente após 3 segundos
        setTimeout(connect, 3000);
    };

    websocket.onerror = function(event) {
        console.error("Erro no WebSocket:", event);
    };
}

function renderDashboard(data, container) {
    let html = "";

    // --- 1. TOP PESQUISAS ---
    html += '<span class="stats-section-title">Top Pesquisas</span>';
    html += '<ul class="top-list">';

    if (data.topSearchTerms && Object.keys(data.topSearchTerms).length > 0) {
        // Ordena por contagem (decrescente) e pega no Top 5
        let sortedSearch = Object.entries(data.topSearchTerms)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);

        sortedSearch.forEach(([term, count], index) => {
            html += `<li><span>${index + 1}. ${term}</span><span class="count-badge">${count}</span></li>`;
        });
    } else {
        html += '<li style="color:#999; font-style:italic; justify-content:center; padding:5px;">Sem dados</li>';
    }
    html += '</ul>';

    // --- 2. BARRELS ---
    html += '<span class="stats-section-title">Estado dos Barrels</span>';

    if (data.barrelDetails && data.barrelDetails.length > 0) {
        data.barrelDetails.forEach(barrel => {
            let isActive = (barrel.status === "Active");
            let statusLabel = isActive ? 'ATIVO' : 'OFFLINE';
            let cardClass = isActive ? '' : 'inactive';

            // Cores para o badge de status
            let badgeColor = isActive ? 'rgba(129, 201, 149, 0.2)' : 'rgba(242, 139, 130, 0.2)';
            let textColor = isActive ? '#81c995' : '#f28b82';

            html += `
                <div class="barrel-card ${cardClass}">
                    <div class="barrel-header">
                        <span>${barrel.name}</span>
                        <span style="font-size:9px; padding:2px 5px; background:${badgeColor}; color:${textColor}; border-radius:4px;">
                            ${statusLabel}
                        </span>
                    </div>
                    <div class="barrel-stats-grid">
                        <div class="stat-item">
                            <span class="stat-label">Palavras:</span>
                            <span class="stat-val">${barrel.invertedIndexCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">Links:</span>
                            <span class="stat-val">${barrel.incomingLinksCount || 0}</span>
                        </div>
                        <div class="stat-item" style="grid-column: span 2; margin-top:2px; border-top:1px dashed #444; padding-top:2px;">
                            <span class="stat-label">Latência:</span>
                            <span class="stat-val" style="color:${textColor}">
                                ${barrel.avgResponseTime ? barrel.avgResponseTime.toFixed(1) : 0}ms
                            </span>
                            <span style="font-size:9px; color:#9aa0a6;">(${barrel.requestCount || 0} reqs)</span>
                        </div>
                    </div>
                </div>`;
        });
    } else {
        html += '<div style="padding:10px; font-size:11px; color:#999; text-align:center;">A aguardar Barrels...</div>';
    }

    container.innerHTML = html;
}