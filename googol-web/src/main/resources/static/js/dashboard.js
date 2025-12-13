var websocket = null;

window.onload = function() {
    connect();
};

function connect() {
    // --- LOG DE VERIFICAÇÃO ---
    console.log("%c >>> DASHBOARD JS: VERSÃO HTTPS ATIVA <<< ", "background: #222; color: #bada55; font-size: 14px");

    // --- 1. Lógica HTTP vs HTTPS ---
    // Se a página for https://, usa wss:// (Secure WebSocket)
    // Se a página for http://, usa ws:// (Normal WebSocket)
    var isSecure = window.location.protocol === 'https:';
    var protocol = isSecure ? 'wss://' : 'ws://';

    // Constrói o URL completo (ex: wss://abcd.ngrok-free.app/stats)
    var wsUri = protocol + window.location.host + '/stats';

    console.log("A tentar conectar a: " + wsUri);

    var statusDot = document.getElementById("status-dot");
    var contentDiv = document.getElementById("stats-content");

    // Verifica suporte do browser
    if ('WebSocket' in window) {
        websocket = new WebSocket(wsUri);
    } else {
        if(contentDiv) contentDiv.innerHTML = "O seu browser não suporta WebSockets.";
        return;
    }

    websocket.onopen = function(event) {
        // Conexão estabelecida: bolinha verde
        if(statusDot) statusDot.classList.add("connected");
        console.log("Sucesso! Conectado via " + protocol);
    };

    websocket.onmessage = function(event) {
        try {
            var data = JSON.parse(event.data);
            if(contentDiv) renderDashboard(data, contentDiv);
        } catch (e) {
            console.error("Erro ao ler JSON recebido:", e);
        }
    };

    websocket.onclose = function(event) {
        // Conexão perdida: bolinha vermelha
        if(statusDot) statusDot.classList.remove("connected");
        console.warn("WebSocket fechado. A tentar reconectar em 3s...");

        // Tenta reconectar automaticamente
        setTimeout(connect, 3000);
    };

    websocket.onerror = function(event) {
        console.error("Erro WebSocket:", event);
    };
}

function renderDashboard(data, container) {
    let html = "";

    // --- 1. TOP PESQUISAS ---
    html += '<span class="stats-section-title">Top Pesquisas</span>';
    html += '<ul class="top-list">';

    if (data.topSearchTerms && Object.keys(data.topSearchTerms).length > 0) {
        let sortedSearch = Object.entries(data.topSearchTerms)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);

        sortedSearch.forEach(([term, count], index) => {
            html += `<li><span>${index + 1}. ${term}</span><span class="count-badge">${count}</span></li>`;
        });
    } else {
        html += '<li style="color:#999; font-style:italic; padding:5px;">Sem dados</li>';
    }
    html += '</ul>';

    // --- 2. BARRELS ---
    html += '<span class="stats-section-title">Estado dos Barrels</span>';

    if (data.barrelDetails && data.barrelDetails.length > 0) {
        data.barrelDetails.forEach(barrel => {
            let isActive = (barrel.status === "Active");
            let statusLabel = isActive ? 'ATIVO' : 'OFFLINE';
            let cardClass = isActive ? '' : 'inactive';
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