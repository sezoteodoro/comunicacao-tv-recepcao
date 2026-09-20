# Comunicação TV – Recepção

Aplicativo Android TV dedicado à Recepção da IASD Botujuru.

## Objetivo

O navegador nativo de muitas Android TVs é limitado. Este projeto incorpora o Mozilla GeckoView dentro do APK e abre diretamente:

`https://comunicacao.adventistasbotujuru.org/?ambiente=recepcao&tv=1`

## Recursos

- tela cheia em paisagem;
- sessão/cookies persistentes;
- navegação por controle remoto;
- foco visual nos elementos selecionados;
- abertura automática de “Acesso restrito” quando a página pública for exibida;
- tentativa automática de reconexão;
- menu oculto segurando VOLTAR por 3 segundos;
- opção de limpar sessão/trocar usuário;
- início automático opcional ao ligar a TV, quando permitido pelo fabricante.

## Motor web

Usa `org.mozilla.geckoview:geckoview-omni:156.0.20260909172920`, reunindo as arquiteturas Android no pacote Omni.

## Build automático no GitHub

O arquivo `.github/workflows/build-apk.yml` executa o build no GitHub Actions e disponibiliza o APK como artefato.

Depois do push para `main`, abra **Actions → Build Android TV APK → execução mais recente → Artifacts** e baixe `ComunicacaoTV-Recepcao-GeckoView-debug`.
