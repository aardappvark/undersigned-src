// tts.swift <voice-identifier> <rate 0..1> <out.caf> <text>: renders speech to a file with AVSpeechSynthesizer.
import AVFoundation
let a = CommandLine.arguments
let synth = AVSpeechSynthesizer()
let u = AVSpeechUtterance(string: a[4])
u.voice = AVSpeechSynthesisVoice(identifier: a[1])
u.rate = Float(a[2]) ?? AVSpeechUtteranceDefaultSpeechRate
var file: AVAudioFile?
var done = false
synth.write(u) { buf in
    guard let pcm = buf as? AVAudioPCMBuffer else { return }
    if pcm.frameLength == 0 { done = true; return }
    if file == nil { file = try? AVAudioFile(forWriting: URL(fileURLWithPath: a[3]), settings: pcm.format.settings, commonFormat: pcm.format.commonFormat, interleaved: pcm.format.isInterleaved) }
    try? file?.write(from: pcm)
}
let end = Date().addingTimeInterval(60)
while !done && Date() < end { RunLoop.main.run(until: Date().addingTimeInterval(0.1)) }
print(file == nil ? "NO AUDIO" : "ok")
