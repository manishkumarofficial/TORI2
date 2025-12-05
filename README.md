# TOR-I - Intelligent Safety Companion

TOR-I is an Android application that prevents driver accidents caused by drowsiness through AI-powered real-time monitoring. The app uses the phone's front camera to detect fatigue, provides audio, vibration, and visual alerts, and sends SOS messages with GPS location if the driver is unresponsive.

## Features

### Core Features
- **Real-time Drowsiness Detection**: Uses MediaPipe Face Mesh to detect eye closure, frequent blinking, and head nodding
- **EAR (Eye Aspect Ratio) Calculation**: Implements the formula `EAR = (||p2 - p6|| + ||p3 - p5||) / (2 * ||p1 - p4||)`
- **Multi-modal Alerts**: Audio alarms, TTS voice messages, and vibration alerts
- **SOS Functionality**: Automatically sends SMS with GPS coordinates to emergency contacts
- **Trip Logging**: Records driving sessions with safety statistics

### Safety Features
- **Break Reminders**: Suggests breaks when drowsiness is detected
- **Emergency Contacts**: Manage and prioritize emergency contacts
- **Location Tracking**: GPS coordinates included in SOS messages
- **Background Monitoring**: Continues monitoring when app is minimized

### Customization
- **Adjustable Thresholds**: Configure EAR threshold, consecutive frames, and alert volume
- **Language Support**: English and Tamil (with transliteration)
- **Low Power Mode**: Reduces camera frame rate to save battery
- **Coaching Tips**: Driver safety tips after drowsiness detection

## Technical Requirements

- **Android SDK**: Minimum 26, Target 34
- **Kotlin**: Latest stable version
- **Camera**: Front camera required
- **Permissions**: Camera, Location, SMS, Vibration, Wake Lock

## Dependencies

The app uses the following key libraries:
- **CameraX**: For camera access and processing
- **MediaPipe**: For face landmark detection
- **TensorFlow Lite**: For on-device ML processing
- **Room**: For local database storage
- **Navigation Component**: For navigation between screens
- **Material Design 3**: For modern UI components

## Setup Instructions

### 1. Clone the Repository
```bash
git clone <repository-url>
cd TORI2
```

### 2. Open in Android Studio
1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the TORI2 folder and select it

### 3. Sync Project
1. Wait for Gradle sync to complete
2. Ensure all dependencies are downloaded

### 4. Add MediaPipe Model
1. Download the MediaPipe Face Mesh model
2. Place the model file in `app/src/main/assets/face_landmarker.task`

### 5. Build and Run
1. Connect an Android device or start an emulator
2. Click "Run" or press Shift+F10
3. Grant all required permissions when prompted

## Permissions

The app requires the following permissions:
- **CAMERA**: For face detection and drowsiness monitoring
- **ACCESS_FINE_LOCATION**: For GPS coordinates in SOS messages
- **ACCESS_COARSE_LOCATION**: Alternative location access
- **SEND_SMS**: For emergency SOS messages
- **VIBRATE**: For tactile alerts
- **WAKE_LOCK**: To keep screen on during monitoring

## Usage

### First Time Setup
1. Launch the app
2. Grant all required permissions
3. Add emergency contacts in Settings
4. Configure detection sensitivity if needed

### Starting Monitoring
1. Tap "Start Monitoring" on the main screen
2. Position your phone so your face is visible in the camera
3. The app will continuously monitor for drowsiness signs

### Emergency SOS
- **Automatic**: Sent if driver doesn't respond to drowsiness alerts
- **Manual**: Tap the red SOS button anytime during monitoring

### Settings Configuration
- **EAR Threshold**: Lower values detect drowsiness earlier (default: 0.25)
- **Consecutive Frames**: Frames needed to trigger alert (default: 10)
- **Alert Volume**: Audio alert volume (0-100%)
- **Language**: Choose between English and Tamil
- **Low Power Mode**: Reduces camera frame rate for battery saving

## Architecture

The app follows Clean Architecture principles:

```
app/
├── ui/                    # UI layer (Activities, Fragments, ViewModels)
├── data/                  # Data layer (Repository, Database, Models)
├── ml/                    # Machine Learning (Drowsiness Detection)
├── camera/                # Camera management
├── alert/                 # Alert system (Audio, Vibration, TTS)
├── location/              # Location services
├── sms/                   # SMS functionality
├── service/               # Background services
└── utils/                 # Utility classes
```

### Key Components

1. **DrowsinessDetector**: MediaPipe-based face landmark detection and EAR calculation
2. **CameraManager**: CameraX integration for real-time camera processing
3. **AlertManager**: Multi-modal alert system with TTS and vibration
4. **LocationManager**: GPS location services for SOS messages
5. **SmsManager**: Emergency SMS functionality
6. **Database**: Room database for trip logs and contacts

## Safety Considerations

- **On-device Processing**: All ML processing happens locally, no data leaves the device
- **Privacy**: Video frames are never stored or transmitted
- **Battery Optimization**: Low power mode reduces battery consumption
- **Emergency Contacts**: Encrypted local storage of contact information

## Troubleshooting

### Common Issues

1. **Camera Permission Denied**
   - Go to Settings > Apps > TOR-I > Permissions
   - Enable Camera permission

2. **No Face Detected**
   - Ensure good lighting
   - Position phone at eye level
   - Remove sunglasses or hats

3. **SOS Not Sending**
   - Check SMS permissions
   - Verify emergency contacts are added
   - Ensure phone has cellular signal

4. **High Battery Usage**
   - Enable Low Power Mode in settings
   - Close other apps during monitoring
   - Use while charging if possible

### Performance Tips

- Use the app in well-lit conditions for better face detection
- Keep the phone stable during monitoring
- Regularly update emergency contacts
- Export trip logs periodically for backup

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please contact [support-email] or create an issue in the repository.

## Acknowledgments

- MediaPipe team for face landmark detection
- Android team for CameraX and Material Design
- TensorFlow team for on-device ML capabilities

---

**⚠️ Safety Notice**: TOR-I is designed to assist drivers but should not replace good driving practices. Always drive responsibly and take breaks when needed.
