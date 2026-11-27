package fr.xephi.authme.mail;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Generates an image containing a password, to be embedded into an email.
 */
public class ImageGenerator {

    /**
     * Method generateImage.
     *
     * @param pass the password to render
     * @return BufferedImage
     */
    public static BufferedImage generateImage(String pass) {
        BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, 200, 40);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("Comic Sans MS", Font.BOLD, 30));
            graphics.drawString(pass, 5, 30);
        } finally {
            graphics.dispose();
        }
        image.flush();
        return image;
    }
}
