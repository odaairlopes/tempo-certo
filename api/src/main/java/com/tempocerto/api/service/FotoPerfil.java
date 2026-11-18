package com.tempocerto.api.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public final class FotoPerfil {
    private FotoPerfil() {}

    public static byte[] preparar(MultipartFile arquivo) {
        if (arquivo.isEmpty() || arquivo.getSize() > 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma foto de até 1 MB.");
        try (var entrada = arquivo.getInputStream(); var imagem = ImageIO.createImageInputStream(entrada)) {
            var leitores = ImageIO.getImageReaders(imagem);
            if (!leitores.hasNext()) throw invalida();
            var leitor = leitores.next();
            try {
                if (!Set.of("JPEG", "PNG").contains(leitor.getFormatName().toUpperCase(java.util.Locale.ROOT))) throw invalida();
                leitor.setInput(imagem);
                int largura = leitor.getWidth(0), altura = leitor.getHeight(0);
                if ((long) largura * altura > 24_000_000 || largura > 10000 || altura > 10000) throw invalida();
                var parametros = leitor.getDefaultReadParam();
                int amostra = Math.max(1, Math.max(largura, altura) / 384);
                parametros.setSourceSubsampling(amostra, amostra, 0, 0);
                var foto = leitor.read(0, parametros);
                double escala = Math.min(1, 320.0 / Math.max(foto.getWidth(), foto.getHeight()));
                var miniatura = new BufferedImage(Math.max(1, (int)(foto.getWidth() * escala)),
                    Math.max(1, (int)(foto.getHeight() * escala)), BufferedImage.TYPE_INT_RGB);
                var g = miniatura.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, miniatura.getWidth(), miniatura.getHeight());
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(foto, 0, 0, miniatura.getWidth(), miniatura.getHeight(), null);
                } finally { g.dispose(); }
                var saida = new ByteArrayOutputStream();
                ImageIO.write(miniatura, "jpeg", saida);
                return saida.toByteArray();
            } finally { leitor.dispose(); }
        } catch (IOException | IllegalArgumentException e) { throw invalida(); }
    }

    private static ResponseStatusException invalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma foto JPEG ou PNG válida.");
    }
}
