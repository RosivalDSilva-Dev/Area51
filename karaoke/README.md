# Karaokê Studio

App web de karaokê para treinar canto e gravar a voz. Funciona no Chrome do Android
(e no computador) e pode ser instalado na tela inicial.

**Endereço (depois de ativar o GitHub Pages):** https://rosivaldsilva-dev.github.io/Area51/karaoke/

## Como usar

1. **Arquivos:** escolha o *Playback* (instrumental + back vocal) e a *Voz-guia*
   (voz principal isolada, no mesmo tom do playback; ela toca no fone como guia). Os arquivos ficam guardados no
   celular; não é preciso escolher de novo.
2. **Tom:** é detectado pelo nome do arquivo de playback ("1 tom abaixo",
   "meio tom abaixo", "original"). A linha-guia de afinação acompanha o tom.
3. **Fone Bluetooth:** use o microfone do celular e faça a **calibração de atraso**
   (diga "tá" junto com 8 batidas).
4. **Letra:** cole a letra, uma linha por frase. O app encaixa as linhas nas
   frases da voz-guia; se ficar fora do tempo, use **Sincronizar** e toque no
   começo de cada linha.
5. **Cantar:** do início ou a partir de qualquer linha. No fim aparece a nota e
   o botão para baixar a gravação em WAV (já alinhada com a música).

## Músicas e gravações no Google Drive (privado)

O site é público, mas os áudios, a letra e as gravações ficam numa pasta
**privada** do Google Drive do usuário, criada pelo próprio app:

```
Karaokê Studio/<nome da música>/Playback - 1 tom abaixo.mp3
Karaokê Studio/<nome da música>/Voz-guia - 1 tom abaixo.mp3
Karaokê Studio/<nome da música>/letra.txt            (opcional)
Karaokê Studio/<nome da música>/sincronizacao.json   (opcional)
Karaokê Studio/<nome da música>/gravações/<gravação>.wav
```

O acesso é feito com **Entrar com Google** (Google Identity Services), com a
permissão `drive.file`: o app só enxerga os arquivos que ele mesmo criou no Drive.
No projeto do Google Cloud é preciso ter a **Google Drive API** ativada e
`https://rosivaldsilva-dev.github.io` nas origens JavaScript autorizadas.

## Privacidade e direitos

O app não contém áudio, letra nem melodia de nenhuma música: tudo é carregado
pelo usuário (do aparelho ou do próprio Google Drive), e a análise da melodia é
feita no navegador. Nada vai para outros servidores além do Drive do usuário.
