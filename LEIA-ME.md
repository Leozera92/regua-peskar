# PESKAR Régua 2.0 — app de teste (Android)

App do piloto: câmera guiada, reconhecimento dos 4 marcadores da régua, correção de perspectiva e medida em mm.

## Gerar o APK sem instalar nada (GitHub, grátis)

1. Crie uma conta em github.com (se ainda não tiver).
2. Clique em **New repository**, dê o nome `peskar-regua` e crie.
3. Na página do repositório, clique em **uploading an existing file** e arraste **todo o conteúdo desta pasta** (incluindo a pasta `.github`). Clique em **Commit changes**.
   - Dica: no Windows/Mac, a pasta `.github` pode ficar oculta. Se o upload não levar ela, crie o arquivo pelo site: **Add file → Create new file**, nome `.github/workflows/gerar-apk.yml`, e cole o conteúdo do arquivo de mesmo nome.
4. Abra a aba **Actions**. A tarefa "Gerar APK" roda sozinha (uns 4 minutos).
5. Quando ficar verde, vá em **Releases** (coluna da direita na página inicial do repositório) e baixe `peskar-regua.apk` direto no celular.
6. No Android, permita "instalar apps desta fonte" quando ele pedir e instale.

Cada vez que você enviar uma alteração, um novo APK é gerado.

## Gerar pelo Android Studio (alternativa)

Abra esta pasta no Android Studio → **Build → Build APK(s)**. O arquivo sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Como testar

1. Imprima `Regua_PESKAR_teste_A4.pdf` em A4 paisagem, **escala 100%**. Confira a linha de 10 cm com uma régua comum.
2. Cole em papelão ou MDF, bem plano.
3. Abra o app → **Abrir câmera** → enquadre até a moldura ficar verde → fotografe.
4. Arraste o ponto verde até a ponta da cauda (use a lupa). O ponto branco confere o focinho no zero.
5. Para medir a precisão, use objetos de tamanho conhecido (cartão de crédito = 85,6 mm; folha A4 em pé = 210 mm de largura).

## Arquivos

- `app/src/main/assets/index.html` — o app inteiro (interface + visão computacional). Para mudar telas ou textos, edite aqui.
- `app/src/main/java/.../MainActivity.java` — casca Android (WebView, permissão de câmera, galeria).
- Geometria da régua: objeto `PK.RULER` dentro do `index.html` (posição dos marcadores em mm). A régua definitiva de 100 cm só precisa atualizar esses números.

## Limites desta versão

- Protótipo de bancada: não substitui a medição oficial do torneio.
- A ponta da cauda é marcada manualmente (leitura automática ainda não implementada).
- Capturas ficam só no aparelho (sem servidor/arbitragem online ainda).
- A leitura do QR Code usa uma biblioteca carregada da internet; sem sinal, o app mede normalmente e mostra "QR não verificado".
- Peixe tem altura: fotografe de cima, paralelo à régua, para reduzir erro de paralaxe.
