package com.LocalService.lsp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

@Service
public class CardGeneratorService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}") 
    private String s3UrlPrefix;

    public CardGeneratorService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String generateAndUploadGoldCard(String providerId, String name, String rating, String profilePhotoUrl) throws IOException {
        // 1. Load the template master gold card canvas from classpath
        ClassPathResource resource = new ClassPathResource("assets/gold_card_template.png");
        BufferedImage canvas = ImageIO.read(resource.getInputStream());
        
        Graphics2D g2d = canvas.createGraphics();
        
        // Activate anti-aliasing for high-quality text and image rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 2. Draw the Provider Profile Image clipped as a Circle
        if (profilePhotoUrl != null && !profilePhotoUrl.isEmpty()) {
            try {
                BufferedImage avatarRaw = ImageIO.read(new URL(profilePhotoUrl));
                
                // Target bounding box dimensions for avatar placement on template
                int size = 180;
                int x = 110; 
                int y = 225;

                // Save original graphics clip state
                Shape originalClip = g2d.getClip();
                
                // Set circular clipping path boundary
                Ellipse2D.Double circularClip = new Ellipse2D.Double(x, y, size, size);
                g2d.setClip(circularClip);
                
                // Draw the picture centered into the bounding matrix box
                g2d.drawImage(avatarRaw, x, y, size, size, null);
                
                // Restore clip context to write normal full-frame strings
                g2d.setClip(originalClip);
                
                // Draw a sleek white border ring around the avatar circle anchor frame
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(4f));
                g2d.draw(circularClip);
            } catch (Exception e) {
                // Fallback graceful execution path if profile picture connection drops
                g2d.setColor(new Color(13, 110, 253)); // Taraas Primary Blue
                g2d.fillOval(110, 225, 180, 180);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, 72));
                g2d.drawString(name.substring(0, 1).toUpperCase(), 175, 335);
            }
        }

        // 3. Draw Provider Name String
        g2d.setColor(new Color(17, 24, 39)); // High-contrast charcoal dark gray
        g2d.setFont(new Font("Arial", Font.BOLD, 48));
        g2d.drawString(name, 340, 290);

        // 4. Draw Rating Metrics Array & Star Badge Context
        g2d.setColor(new Color(31, 41, 55));
        g2d.setFont(new Font("Arial", Font.PLAIN, 28));
        g2d.drawString(rating + " / 5.0 Rating", 340, 340);
        
        g2d.dispose(); // Complete graphics pipeline execution trace context cleanly

        // 5. Convert compiled image into an output byte stream matrix array
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", os);
        byte[] buffer = os.toByteArray();

        // 6. Upload directly to AWS S3 bucket destination path keys
        String s3Key = "cards/provider-" + providerId + ".png";
        
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("image/png")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(buffer));

        // Return the clean public absolute image URI context link
        return s3UrlPrefix + bucketName + "/" + s3Key;
    }
}