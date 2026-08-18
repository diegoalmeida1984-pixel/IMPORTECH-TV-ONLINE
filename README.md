# IMPORTECH TV ONLINE

Primeira versão de teste do agente Android TV/TV Box.

## O que esta versão faz
- Gera e guarda um código único no formato ITV-123456.
- Pode ser instalada antes de a box ter internet.
- Quando a internet aparece, tenta conectar automaticamente.
- Abre a tela de QR Code da box.
- Mantém um serviço de comunicação ativo enquanto o Android permitir.
- Recebe os comandos atuais do Painel ADM: vincular, testar comunicação e mandar aviso.
- Tenta iniciar o agente novamente quando a box reinicia.

## Compilar no GitHub
O workflow `.github/workflows/build-apk.yml` compila automaticamente.
Depois de uma execução concluída, abra Actions > Gerar APK IMPORTECH TV ONLINE
e baixe o artefato `IMPORTECH-TV-ONLINE`.

Observação: esta é uma versão de teste para instalação manual (sideload).
Funções de abrir, atualizar, instalar ou desinstalar outros apps ainda não estão implementadas.
