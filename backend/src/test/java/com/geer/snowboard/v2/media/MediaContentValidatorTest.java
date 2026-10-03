package com.geer.snowboard.v2.media;
import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.media.adapter.out.inspection.MediaContentProbe;
import com.geer.snowboard.v2.media.application.port.out.MediaFailure;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class MediaContentValidatorTest {
 @TempDir Path temp;
 @Test void checksRealImageDimensionsAndRejectsDisguisedAndOversizedFiles()throws Exception{
  var probe=new MediaContentProbe();Path png=temp.resolve("photo.png");ImageIO.write(new BufferedImage(40,30,BufferedImage.TYPE_INT_RGB),"png",png.toFile());
  var result=probe.inspect(png,"HERO","image/png");assertThat(result.width()).isEqualTo(40);assertThat(result.height()).isEqualTo(30);
  assertThatThrownBy(()->probe.inspect(png,"HERO","image/jpeg")).isInstanceOf(MediaFailure.class);
  Path html=temp.resolve("fake.png");Files.writeString(html,"<html>not a photo</html>");assertThatThrownBy(()->probe.inspect(html,"HERO","image/png")).isInstanceOf(MediaFailure.class);
  Path big=temp.resolve("big.png");ImageIO.write(new BufferedImage(4097,1,BufferedImage.TYPE_INT_RGB),"png",big.toFile());assertThatThrownBy(()->probe.inspect(big,"HERO","image/png")).isInstanceOf(MediaFailure.class);
 }
 @Test void acceptsRealH264Mp4AndRejectsQuickTimeBrand()throws Exception{
  Path valid=temp.resolve("valid.mp4");try(var input=getClass().getResourceAsStream("/media/valid-h264.mp4")){Files.copy(input,valid);}
  var probe=new MediaContentProbe();var result=probe.inspect(valid,"HIGHLIGHT_VIDEO","video/mp4");
  assertThat(result.width()).isEqualTo(320);assertThat(result.height()).isEqualTo(180);assertThat(result.duration()).isBetween(0.9,1.1);
  byte[] qt=Files.readAllBytes(valid);System.arraycopy(new byte[]{'q','t',' ',' '},0,qt,8,4);
  Path quicktime=temp.resolve("quicktime.mp4");Files.write(quicktime,qt);
  assertThatThrownBy(()->probe.inspect(quicktime,"HIGHLIGHT_VIDEO","video/mp4")).isInstanceOf(MediaFailure.class);
 }
 @Test void rejectsInvalidVideoWithoutAcceptingAnExtension()throws Exception{
  Path fake=temp.resolve("fake.mp4");Files.writeString(fake,"not an mp4");assertThatThrownBy(()->new MediaContentProbe().inspect(fake,"HIGHLIGHT_VIDEO","video/mp4")).isInstanceOf(MediaFailure.class);
 }
}
