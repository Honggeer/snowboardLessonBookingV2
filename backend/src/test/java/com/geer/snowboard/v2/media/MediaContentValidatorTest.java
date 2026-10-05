package com.geer.snowboard.v2.media;
import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.media.adapter.out.inspection.MediaContentProbe;
import com.geer.snowboard.v2.media.application.port.out.MediaFailure;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
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
 @Test void rejectsCorruptedFramesEvenWhenTheContainerMetadataIsValid()throws Exception{
  byte[] corrupted;try(var input=getClass().getResourceAsStream("/media/valid-h264.mp4")){corrupted=input.readAllBytes();}
  java.util.Arrays.fill(corrupted,corrupted.length-20,corrupted.length,(byte)0);
  Path video=temp.resolve("corrupted.mp4");Files.write(video,corrupted);
  assertThatThrownBy(()->new MediaContentProbe().inspect(video,"HIGHLIGHT_VIDEO","video/mp4"))
   .isInstanceOfSatisfying(MediaFailure.class,failure->{assertThat(failure.code()).isEqualTo("INVALID_CONTENT");assertThat(failure.invalid()).isTrue();});
 }
 @Test void defaultBudgetAcceptsAValidProbeTakingLongerThanThirtySeconds()throws Exception{
  Path executable=delayedProbe(31);
  var result=new MediaContentProbe(executable.toString()).inspect(temp.resolve("video.mp4"),"HIGHLIGHT_VIDEO","video/mp4");
  assertThat(result.width()).isEqualTo(1920);assertThat(result.height()).isEqualTo(1080);assertThat(result.duration()).isEqualTo(65.292993);
 }
 @Test void configuredDeadlineIsTemporaryAndStopsTheProbeProcess()throws Exception{
  Path executable=delayedProbe(2),video=temp.resolve("video.mp4");
  try(var context=configuredProbe(executable,1)){
   assertThatThrownBy(()->context.getBean(MediaContentProbe.class).inspect(video,"HIGHLIGHT_VIDEO","video/mp4"))
    .isInstanceOfSatisfying(MediaFailure.class,failure->{assertThat(failure.code()).isEqualTo("PROBE_TIMEOUT");assertThat(failure.invalid()).isFalse();});
   long pid=Long.parseLong(Files.readString(Path.of(video+".pid")));
   var process=ProcessHandle.of(pid);
   if(process.isPresent())process.get().onExit().get(3,java.util.concurrent.TimeUnit.SECONDS);
   assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
  }
 }
 @Test void configuredDeadlineMustStayWithinTheResourceBudget(){
  for(int timeout:new int[]{0,91})assertThatThrownBy(()->{try(var context=configuredProbe(Path.of("ffprobe"),timeout)){context.getBean(MediaContentProbe.class);}})
   .hasRootCauseInstanceOf(IllegalArgumentException.class);
 }
 private AnnotationConfigApplicationContext configuredProbe(Path executable,int seconds){
  var context=new AnnotationConfigApplicationContext();
  context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("probe-test",java.util.Map.of("media.ffprobe-path",executable.toString(),"media.ffprobe-timeout-seconds",seconds)));
  context.register(MediaContentProbe.class);
  try{context.refresh();return context;}catch(RuntimeException e){context.close();throw e;}
 }
 private Path delayedProbe(int seconds)throws Exception{
  Path executable=temp.resolve("delayed-probe");
  Files.writeString(executable,"""
   #!/usr/bin/env python3
   import os, sys, time
   from pathlib import Path
   Path(sys.argv[-1] + '.pid').write_text(str(os.getpid()))
   time.sleep(%d)
   print('{"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"65.292993","tags":{"major_brand":"mp42"}},"streams":[{"codec_type":"video","codec_name":"h264","pix_fmt":"yuv420p","width":1920,"height":1080,"avg_frame_rate":"30/1"},{"codec_type":"audio","codec_name":"aac"}]}')
   """.formatted(seconds));
  assertThat(executable.toFile().setExecutable(true)).isTrue();return executable;
 }
}
