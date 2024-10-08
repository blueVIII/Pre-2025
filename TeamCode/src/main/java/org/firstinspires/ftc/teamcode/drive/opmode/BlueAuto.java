package org.firstinspires.ftc.teamcode.drive.opmode;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.teamcode.drive.DriveConstants;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.tfod.TfodProcessor;

import org.openftc.apriltag.AprilTagDetection;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import java.util.List;

@Autonomous(name = "BlueAuto", group = "Concept")
public class BlueAuto extends LinearOpMode{
    private static final String TFOD_MODEL_ASSET = "model.tflite";
    private static final String[] LABELS = {"box"};
    private TfodProcessor tfod;

    /**
     * The variable to store our instance of the vision portal.
     */
    private VisionPortal visionPortal;

    //movement
    OpenCvCamera camera;
    AprilTagDetectionPipeline aprilTagDetectionPipeline;
    static final double FEET_PER_METER = 3.28084;
    private double lastError = 0;
    ElapsedTime timer = new ElapsedTime();

    // Lens intrinsics
    // UNITS ARE PIXELS
    // NOTE: this calibration is for the C920 webcam at 800x448.
    // You will need to do your own calibration for other configurations!
    double fx = 578.272;
    double fy = 578.272;
    double cx = 402.145;
    double cy = 221.506;
    int targetPosition = 1;
    int currentPosition;

    //true == up
    boolean direction = true;
    double beginTime, currentTime, timeRemaining;

    // UNITS ARE METERS
    double tagsize = 0.166;

    int LEFT = 1;
    int MIDDLE = 2;
    int RIGHT = 3;

    String detection;

    double xCoordinate;
    double yCoordinate;


    @Override
    public void runOpMode() { // code to run after init
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap);
//        double distanceInches = 1;
//        double placeHolderDistance = 1;
        initTfod();

        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        camera = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "webcam"), cameraMonitorViewId);
        camera.setPipeline(aprilTagDetectionPipeline);
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener() {
            @Override
            public void onOpened() {
                camera.startStreaming(800, 448, OpenCvCameraRotation.UPRIGHT);
            }
            @Override
            public void onError(int errorCode) {
            }
        });

        telemetry.setMsTransmissionInterval(50);

        // starting position
        Pose2d startingPose = new Pose2d(12, 60, Math.toRadians(270));

        while (!isStarted() && !isStopRequested()) {
            updateTfod();// Push telemetry to the Driver Station.
            drive.pixelServo.setPosition(0);
            telemetry.update();
        }

        waitForStart();

        while (opModeIsActive()) {
            drive.setPoseEstimate(startingPose);
            if (detection == "left") {
                TrajectorySequence left = drive.trajectorySequenceBuilder(startingPose)
                        .splineTo(new Vector2d(16, 30), Math.toRadians(0))
                        .addTemporalMarker(() -> {
                            drive.pixelServo.setPosition(1);
                        })
                        .back(8, SampleMecanumDrive.getVelocityConstraint(30, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH),
                                SampleMecanumDrive.getAccelerationConstraint(60))
                        .strafeLeft(15, SampleMecanumDrive.getVelocityConstraint(30, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH),
                                SampleMecanumDrive.getAccelerationConstraint(60))
                        .forward(40, SampleMecanumDrive.getVelocityConstraint(30, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH),
                                SampleMecanumDrive.getAccelerationConstraint(60))
                        .build();
                drive.followTrajectorySequence(left);
                drive.breakFollowing();
                break;
            }
            else if (detection == "middle") {
                TrajectorySequence middle = drive.trajectorySequenceBuilder(startingPose)
                        .lineToLinearHeading(new Pose2d(12, 30, Math.toRadians(270)))
                        .addTemporalMarker(() -> {
                            drive.pixelServo.setPosition(1);
                        })
                        .back(15, SampleMecanumDrive.getVelocityConstraint(30, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH),
                                SampleMecanumDrive.getAccelerationConstraint(60))
                        .lineToLinearHeading(new Pose2d(50, 30, Math.toRadians(0)))
                        .build();
                    drive.followTrajectorySequence(middle);
                    drive.breakFollowing();
                    break;
            }
            else {
                TrajectorySequence right = drive.trajectorySequenceBuilder(startingPose)
                        .splineTo(new Vector2d(6, 30), Math.toRadians(-180))
                        .addTemporalMarker(() -> {
                            drive.pixelServo.setPosition(1);
                        })
//                        .lineToLinearHeading(new Pose2d(50, -30, Math.toRadians(180)))
                        .back(40, SampleMecanumDrive.getVelocityConstraint(30, DriveConstants.MAX_ANG_VEL, DriveConstants.TRACK_WIDTH),
                                SampleMecanumDrive.getAccelerationConstraint(60))
                        .build();
                    drive.followTrajectorySequence(right);
                    drive.breakFollowing();
                    break;
            }
        }
    }

    private void initTfod() { // create model detector
        // Create the TensorFlow processor by using a builder.
        tfod = new TfodProcessor.Builder()
                .setModelAssetName("model.tflite")
                .build();

        // Create the vision portal by using a builder.
        VisionPortal.Builder builder = new VisionPortal.Builder();

        // Set the camera (webcam vs. built-in RC phone camera).
        builder.setCamera(hardwareMap.get(WebcamName.class, "webcam"));


        // Enable the RC preview (LiveView).  Set "false" to omit camera monitoring.
        builder.enableLiveView(true);

        // Set the stream format; MJPEG uses less bandwidth than default YUY2.
        //builder.setStreamFormat(VisionPortal.StreamFormat.YUY2);

        // Choose whether or not LiveView stops if no processors are enabled.
        // If set "true", monitor shows solid orange screen if no processors enabled.
        // If set "false", monitor shows camera view without annotations.
        //builder.setAutoStopLiveView(false);

        // Set and enable the processor.
        builder.addProcessor(tfod);

        // Build the Vision Portal, using the above settings.
        visionPortal = builder.build();

        // Set confidence threshold for TFOD recognitions, at any time.
        tfod.setMinResultConfidence(0.7f);

        // Disable or re-enable the TFOD processor at any time.
        //visionPortal.setProcessorEnabled(tfod, true);

    }

    private void updateTfod() { // updates the model detection and add to telemetry
        List<Recognition> currentRecognitions = tfod.getRecognitions();
        telemetry.addData("# Objects Detected", currentRecognitions.size());
        // Step through the list of recognitions and display info for each one.
        for (Recognition recognition : currentRecognitions) {
            double x = (recognition.getLeft() + recognition.getRight()) / 2;
            double y = (recognition.getTop() + recognition.getBottom()) / 2;
            xCoordinate = x;
            yCoordinate = y;
            telemetry.addData("", " ");
            telemetry.addData("Image", "%s (%.0f %% Conf.)", recognition.getLabel(), recognition.getConfidence() * 100);
            telemetry.addData("- Position", "%.0f / %.0f / %s", x, y, detection);
            telemetry.addData("- Size", "%.0f x %.0f", recognition.getWidth(), recognition.getHeight());
        }
        if (xCoordinate < 200) {
            detection = "left";
        }
        else if (xCoordinate < 500) {
            detection = "middle";
        }
        else {
            detection = "right";
        }
    }


//    public void liftUpdate(SampleMecanumDrive drive) {
//        currentPosition = drive.liftMotor1.getCurrentPosition();
//        if (targetPosition == 0){
//        }
//        else if (currentPosition < targetPosition && direction == true) {
//            double power = returnPower(targetPosition, drive.liftMotor1.getCurrentPosition());
//            drive.liftMotor1.setPower(power);
//            drive.liftMotor2.setPower(power);
//
//        } else if (currentPosition > targetPosition && direction == false) {
//            double power = returnPower(targetPosition, drive.liftMotor1.getCurrentPosition());
//            drive.liftMotor1.setPower(power);
//            drive.liftMotor2.setPower(power);
//
//        }
//        else if (currentPosition+10 > targetPosition && direction == true){
//            drive.liftMotor1.setPower(0.05);
//            drive.liftMotor2.setPower(0.05);
//        }
//        else if (currentPosition+10 < targetPosition && direction == false){
//            drive.liftMotor1.setPower(0.05);
//            drive.liftMotor2.setPower(0.05);
//        }
//    }

//    public double returnPower(double reference, double state) {
//        double error = reference - state;
//        double derivative = (error - lastError) / timer.seconds();
//        lastError = error;
//
//        double output = (error * 0.03) + (derivative * 0.0002) + 0.05;
//        return output;
//    }
}