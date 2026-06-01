(function() {
    'use strict';

    var Avatar3D = {};

    Avatar3D.init = function(containerId, options) {
        options = options || {};
        var container = document.getElementById(containerId);
        if (!container) return;

        var width = container.clientWidth || 180;
        var height = container.clientHeight || 160;

        var scene = new THREE.Scene();

        var camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
        camera.position.set(0, 0.8, 6);
        camera.lookAt(0, 0, 0);

        var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        renderer.setSize(width, height);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        renderer.setClearColor(0x000000, 0);
        renderer.shadowMap.enabled = true;
        container.appendChild(renderer.domElement);

        var ambientLight = new THREE.AmbientLight(0x404060, 1.5);
        scene.add(ambientLight);

        var mainLight = new THREE.DirectionalLight(0xffffff, 1.2);
        mainLight.position.set(2, 3, 4);
        scene.add(mainLight);

        var rimLight = new THREE.DirectionalLight(0x3b82f6, 0.8);
        rimLight.position.set(-2, 1, -1);
        scene.add(rimLight);

        var bottomLight = new THREE.DirectionalLight(0x8b5cf6, 0.4);
        bottomLight.position.set(0, -2, 1);
        scene.add(bottomLight);

        var bodyGroup = new THREE.Group();
        var headGroup = new THREE.Group();
        var leftArmGroup = new THREE.Group();
        var rightArmGroup = new THREE.Group();
        scene.add(bodyGroup);
        scene.add(headGroup);
        scene.add(leftArmGroup);
        scene.add(rightArmGroup);

        var bodyMat = new THREE.MeshPhongMaterial({
            color: 0x4f6ef7,
            specular: 0x222244,
            shininess: 40,
            flatShading: false
        });

        var bodyGeo = new THREE.SphereGeometry(0.8, 32, 32);
        bodyGeo.scale(1, 0.8, 0.7);
        var body = new THREE.Mesh(bodyGeo, bodyMat);
        body.position.y = -0.1;
        body.castShadow = true;
        bodyGroup.add(body);

        var bellyGeo = new THREE.SphereGeometry(0.45, 32, 32);
        bellyGeo.scale(1, 0.7, 0.5);
        var bellyMat = new THREE.MeshPhongMaterial({
            color: 0x7c93f9,
            specular: 0x111122,
            shininess: 20
        });
        var belly = new THREE.Mesh(bellyGeo, bellyMat);
        belly.position.y = -0.15;
        belly.position.z = 0.35;
        bodyGroup.add(belly);

        var headGeo = new THREE.SphereGeometry(0.62, 32, 32);
        var headMat = new THREE.MeshPhongMaterial({
            color: 0x4f6ef7,
            specular: 0x333355,
            shininess: 50
        });
        var head = new THREE.Mesh(headGeo, headMat);
        head.position.y = 0.7;
        head.castShadow = true;
        headGroup.add(head);

        var facePlateGeo = new THREE.SphereGeometry(0.48, 32, 32);
        facePlateGeo.scale(1, 0.9, 0.3);
        var facePlateMat = new THREE.MeshPhongMaterial({
            color: 0x1e293b,
            specular: 0x111111,
            shininess: 10
        });
        var facePlate = new THREE.Mesh(facePlateGeo, facePlateMat);
        facePlate.position.z = 0.28;
        facePlate.position.y = 0.7;
        headGroup.add(facePlate);

        var eyeGeo = new THREE.SphereGeometry(0.1, 16, 16);
        var eyeMat = new THREE.MeshPhongMaterial({
            color: 0x60a5fa,
            emissive: 0x3b82f6,
            emissiveIntensity: 0.8
        });

        var leftEye = new THREE.Mesh(eyeGeo, eyeMat);
        leftEye.position.set(-0.18, 0.78, 0.52);
        headGroup.add(leftEye);

        var rightEye = new THREE.Mesh(eyeGeo, eyeMat);
        rightEye.position.set(0.18, 0.78, 0.52);
        headGroup.add(rightEye);

        var pupilGeo = new THREE.SphereGeometry(0.05, 8, 8);
        var pupilMat = new THREE.MeshPhongMaterial({
            color: 0xffffff,
            emissive: 0xffffff,
            emissiveIntensity: 1
        });

        var leftPupil = new THREE.Mesh(pupilGeo, pupilMat);
        leftPupil.position.set(-0.18, 0.78, 0.6);
        headGroup.add(leftPupil);

        var rightPupil = new THREE.Mesh(pupilGeo, pupilMat);
        rightPupil.position.set(0.18, 0.78, 0.6);
        headGroup.add(rightPupil);

        var mouthGeo = new THREE.TorusGeometry(0.08, 0.02, 8, 8, Math.PI);
        var mouthMat = new THREE.MeshPhongMaterial({
            color: 0x94a3b8,
            specular: 0x000000,
            shininess: 5
        });
        var mouth = new THREE.Mesh(mouthGeo, mouthMat);
        mouth.position.set(0, 0.64, 0.55);
        mouth.rotation.z = Math.PI;
        headGroup.add(mouth);

        var antennaGeo = new THREE.CylinderGeometry(0.03, 0.04, 0.25, 8);
        var antennaMat = new THREE.MeshPhongMaterial({
            color: 0x64748b,
            specular: 0x222222,
            shininess: 40
        });
        var antenna = new THREE.Mesh(antennaGeo, antennaMat);
        antenna.position.y = 1.05;
        headGroup.add(antenna);

        var antennaBallGeo = new THREE.SphereGeometry(0.08, 16, 16);
        var antennaBallMat = new THREE.MeshPhongMaterial({
            color: 0xfbbf24,
            emissive: 0xfbbf24,
            emissiveIntensity: 0.5
        });
        var antennaBall = new THREE.Mesh(antennaBallGeo, antennaBallMat);
        antennaBall.position.y = 1.2;
        headGroup.add(antennaBall);

        var earGeo = new THREE.SphereGeometry(0.1, 16, 16);
        earGeo.scale(0.6, 1, 1);
        var earMat = new THREE.MeshPhongMaterial({
            color: 0x3b5de7,
            specular: 0x222244,
            shininess: 30
        });

        var leftEar = new THREE.Mesh(earGeo, earMat);
        leftEar.position.set(-0.58, 0.75, 0);
        headGroup.add(leftEar);

        var rightEar = new THREE.Mesh(earGeo, earMat);
        rightEar.position.set(0.58, 0.75, 0);
        headGroup.add(rightEar);

        var armGeo = new THREE.CapsuleGeometry(0.12, 0.5, 8, 12);
        var armMat = new THREE.MeshPhongMaterial({
            color: 0x4f6ef7,
            specular: 0x222244,
            shininess: 30
        });

        var leftArm = new THREE.Mesh(armGeo, armMat);
        leftArm.position.set(0, -0.3, 0);
        leftArm.castShadow = true;
        leftArmGroup.add(leftArm);
        leftArmGroup.position.set(-0.85, 0, 0);
        leftArmGroup.rotation.z = 0.3;

        var rightArm = new THREE.Mesh(armGeo, armMat);
        rightArm.position.set(0, -0.3, 0);
        rightArm.castShadow = true;
        rightArmGroup.add(rightArm);
        rightArmGroup.position.set(0.85, 0, 0);
        rightArmGroup.rotation.z = -0.3;

        var handGeo = new THREE.SphereGeometry(0.14, 16, 16);
        var handMat = new THREE.MeshPhongMaterial({
            color: 0x3b5de7,
            specular: 0x222244,
            shininess: 30
        });

        var leftHand = new THREE.Mesh(handGeo, handMat);
        leftHand.position.set(0, -0.6, 0);
        leftArmGroup.add(leftHand);

        var rightHand = new THREE.Mesh(handGeo, handMat);
        rightHand.position.set(0, -0.6, 0);
        rightArmGroup.add(rightHand);

        var ringGeo = new THREE.TorusGeometry(0.7, 0.03, 8, 32);
        var ringMat = new THREE.MeshPhongMaterial({
            color: 0x60a5fa,
            emissive: 0x3b82f6,
            emissiveIntensity: 0.3,
            transparent: true,
            opacity: 0.5
        });
        var ring = new THREE.Mesh(ringGeo, ringMat);
        ring.rotation.x = Math.PI / 2;
        ring.position.y = -0.65;
        ring.name = 'platformRing';
        scene.add(ring);

        var state = {
            currentAction: 'idle',
            targetAction: 'idle',
            actionTimer: 0,
            actionDuration: 0,
            transitionProgress: 0,
            idleTime: 0,
            bodyBobOffset: 0,
            headTiltX: 0,
            headTiltZ: 0,
            leftArmAngle: 0.3,
            rightArmAngle: -0.3,
            eyeScale: 1,
            mouthScaleY: 1,
            antennaGlow: 0.5,
            ringScale: 1,
            ringOpacity: 0.5,
            isLevelUp: false,
            levelUpTime: 0,
            particles: []
        };

        var actionConfig = {
            idle: { duration: 0, loop: true },
            thinking: { duration: 2.5, loop: true },
            tool_executing: { duration: 1.5, loop: true },
            level_up: { duration: 2.0, loop: false },
            excited: { duration: 1.0, loop: true },
            confused: { duration: 1.8, loop: true },
            wave: { duration: 1.2, loop: true },
            sleep: { duration: 3.0, loop: true },
            typing: { duration: 1.5, loop: true },
            celebrate: { duration: 2.0, loop: true }
        };

        var starParticles = [];

        function createStarParticle() {
            var starGeo = new THREE.SphereGeometry(0.03, 4, 4);
            var starMat = new THREE.MeshPhongMaterial({
                color: 0xfbbf24,
                emissive: 0xfbbf24,
                emissiveIntensity: 1
            });
            var star = new THREE.Mesh(starGeo, starMat);
            star.position.set(
                (Math.random() - 0.5) * 2,
                Math.random() * 1.5,
                (Math.random() - 0.5) * 2
            );
            star.userData = {
                velocity: new THREE.Vector3(
                    (Math.random() - 0.5) * 0.02,
                    Math.random() * 0.03 + 0.02,
                    (Math.random() - 0.5) * 0.02
                ),
                life: 1.0,
                decay: Math.random() * 0.01 + 0.008
            };
            scene.add(star);
            starParticles.push(star);
        }

        function updateStarParticles() {
            for (var i = starParticles.length - 1; i >= 0; i--) {
                var star = starParticles[i];
                star.userData.life -= star.userData.decay;
                star.position.add(star.userData.velocity);
                star.material.opacity = star.userData.life;
                star.material.transparent = true;
                if (star.userData.life <= 0) {
                    scene.remove(star);
                    star.geometry.dispose();
                    star.material.dispose();
                    starParticles.splice(i, 1);
                }
            }
        }

        function setAction(action) {
            if (state.targetAction === action) return;
            if (action === 'level_up' && state.targetAction === 'level_up') return;
            state.targetAction = action;
            state.transitionProgress = 0;
            state.actionTimer = 0;
        }

        function lerp(a, b, t) {
            return a + (b - a) * t;
        }

        function easeInOutQuad(t) {
            return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        }

        function updateIdleAnimation(dt) {
            state.idleTime += dt;
            state.bodyBobOffset = Math.sin(state.idleTime * 1.5) * 0.08;
            state.headTiltX = Math.sin(state.idleTime * 0.7) * 0.05;
            state.headTiltZ = Math.sin(state.idleTime * 0.9 + 1) * 0.05;
            state.leftArmAngle = 0.3 + Math.sin(state.idleTime * 1.2) * 0.05;
            state.rightArmAngle = -0.3 + Math.sin(state.idleTime * 1.2 + Math.PI) * 0.05;
            state.eyeScale = 1;
            state.mouthScaleY = 1;
            state.antennaGlow = 0.5 + Math.sin(state.idleTime * 2) * 0.2;
            state.ringScale = 1;
            state.ringOpacity = 0.5;
        }

        function updateThinkingAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.sin(t * 0.8) * 0.04;
            state.headTiltX = 0.15 + Math.sin(t * 1.5) * 0.08;
            state.headTiltZ = Math.sin(t * 0.6) * 0.1;
            state.leftArmAngle = 0.8 + Math.sin(t * 2) * 0.1;
            state.rightArmAngle = -0.2 + Math.sin(t * 1.5) * 0.08;
            state.eyeScale = 1 + Math.sin(t * 3) * 0.15;
            state.mouthScaleY = 0.6;
            state.antennaGlow = 0.7 + Math.sin(t * 3) * 0.3;
            state.ringScale = 1;
            state.ringOpacity = 0.3;
        }

        function updateToolExecutingAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.sin(t * 3) * 0.03;
            state.headTiltX = Math.sin(t * 2.5) * 0.03;
            state.headTiltZ = 0;
            state.leftArmAngle = 0.3 + Math.abs(Math.sin(t * 8)) * 0.4;
            state.rightArmAngle = -0.3 - Math.abs(Math.sin(t * 8 + 0.5)) * 0.4;
            state.eyeScale = 1;
            state.mouthScaleY = 1;
            state.antennaGlow = 0.7 + Math.sin(t * 6) * 0.3;
            state.ringScale = 0.9 + Math.sin(t * 4) * 0.1;
            state.ringOpacity = 0.6;
        }

        function updateLevelUpAnimation(dt) {
            state.actionTimer += dt;
            state.levelUpTime += dt;
            var t = state.actionTimer;
            var totalDuration = actionConfig.level_up.duration;

            if (t < 0.3) {
                var prep = t / 0.3;
                state.bodyBobOffset = -prep * 0.3;
                state.headTiltX = 0;
                state.eyeScale = 1 + prep * 0.3;
                state.antennaGlow = 0.5 + prep * 0.5;
                state.ringScale = 1 + prep * 0.2;
                state.ringOpacity = 0.5 + prep * 0.5;
            } else if (t < 0.8) {
                var jump = (t - 0.3) / 0.5;
                state.bodyBobOffset = -0.3 + jump * 1.2;
                if (jump > 0.5) {
                    state.bodyBobOffset = -0.3 + (1 - jump) * 1.2;
                }
                state.headTiltX = -0.1;
                state.eyeScale = 1.5;
                state.antennaGlow = 1;
                state.ringScale = 1.3;
                state.ringOpacity = 0.8;
                if (Math.random() < 0.3) createStarParticle();
            } else if (t < 1.5) {
                var settle = (t - 0.8) / 0.7;
                state.bodyBobOffset = Math.sin(settle * Math.PI * 3) * (1 - settle) * 0.15;
                state.headTiltX = 0;
                state.eyeScale = 1.5 - settle * 0.5;
                state.antennaGlow = 1 - settle * 0.5;
                state.ringScale = 1.3 - settle * 0.3;
                state.ringOpacity = 0.8 - settle * 0.3;
                if (Math.random() < 0.15) createStarParticle();
            } else {
                if (state.targetAction === 'level_up') {
                    state.targetAction = 'idle';
                    state.transitionProgress = 0;
                }
            }

            state.leftArmAngle = 0.3 + Math.sin(t * 5) * 0.2;
            state.rightArmAngle = -0.3 - Math.sin(t * 5) * 0.2;
            state.mouthScaleY = 1.5;
        }

        function updateExcitedAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.abs(Math.sin(t * 4)) * 0.2;
            state.headTiltX = Math.sin(t * 3) * 0.1;
            state.headTiltZ = Math.sin(t * 2.5) * 0.1;
            state.leftArmAngle = 0.3 + Math.sin(t * 5) * 0.3;
            state.rightArmAngle = -0.3 - Math.sin(t * 5) * 0.3;
            state.eyeScale = 1.2 + Math.sin(t * 4) * 0.1;
            state.mouthScaleY = 1.3;
            state.antennaGlow = 0.8 + Math.sin(t * 5) * 0.2;
            state.ringScale = 1 + Math.sin(t * 4) * 0.1;
            state.ringOpacity = 0.7;
        }

        function updateConfusedAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.sin(t * 0.5) * 0.04;
            state.headTiltX = Math.sin(t * 1.2) * 0.2;
            state.headTiltZ = 0;
            state.leftArmAngle = 0.6;
            state.rightArmAngle = -0.6;
            state.eyeScale = 0.7 + Math.sin(t * 2) * 0.1;
            state.mouthScaleY = 0.7;
            state.antennaGlow = 0.3 + Math.sin(t * 1.5) * 0.2;
            state.ringScale = 0.95;
            state.ringOpacity = 0.3;
        }

        function updateWaveAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.sin(t * 1.5) * 0.05;
            state.headTiltX = 0;
            state.headTiltZ = 0.05;
            state.leftArmAngle = 0.3;
            state.rightArmAngle = -0.3 + Math.sin(t * 4) * 0.5;
            state.eyeScale = 1;
            state.mouthScaleY = 1.1;
            state.antennaGlow = 0.6;
            state.ringScale = 1;
            state.ringOpacity = 0.5;
        }

        function updateSleepAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.sin(t * 0.6) * 0.04;
            state.headTiltX = 0.25;
            state.headTiltZ = 0.05;
            state.leftArmAngle = 0.5;
            state.rightArmAngle = -0.5;
            state.eyeScale = 0.15;
            state.mouthScaleY = 0.5;
            state.antennaGlow = 0.1 + Math.sin(t * 0.5) * 0.05;
            state.ringScale = 0.9;
            state.ringOpacity = 0.15;
        }

        function updateTypingAnimation(dt) {
            updateToolExecutingAnimation(dt);
        }

        function updateCelebrateAnimation(dt) {
            state.actionTimer += dt;
            var t = state.actionTimer;
            state.bodyBobOffset = Math.abs(Math.sin(t * 4)) * 0.25;
            state.headTiltX = Math.sin(t * 3) * 0.15;
            state.headTiltZ = 0;
            state.leftArmAngle = 0.3 + Math.sin(t * 5) * 0.5;
            state.rightArmAngle = -0.3 - Math.sin(t * 5) * 0.5;
            state.eyeScale = 1.1;
            state.mouthScaleY = 1.4;
            state.antennaGlow = 0.9 + Math.sin(t * 6) * 0.1;
            state.ringScale = 1 + Math.sin(t * 4) * 0.15;
            state.ringOpacity = 0.8;
            if (Math.random() < 0.1) createStarParticle();
        }

        var animationUpdaters = {
            idle: updateIdleAnimation,
            thinking: updateThinkingAnimation,
            tool_executing: updateToolExecutingAnimation,
            level_up: updateLevelUpAnimation,
            excited: updateExcitedAnimation,
            confused: updateConfusedAnimation,
            wave: updateWaveAnimation,
            sleep: updateSleepAnimation,
            typing: updateTypingAnimation,
            celebrate: updateCelebrateAnimation
        };

        function applyState() {
            bodyGroup.position.y = state.bodyBobOffset;
            headGroup.position.y = state.bodyBobOffset;
            headGroup.rotation.x = state.headTiltX;
            headGroup.rotation.z = state.headTiltZ;
            leftArmGroup.rotation.z = state.leftArmAngle;
            rightArmGroup.rotation.z = state.rightArmAngle;

            leftEye.scale.setScalar(state.eyeScale);
            rightEye.scale.setScalar(state.eyeScale);

            var mouthScale = mouth.scale.clone();
            mouthScale.y = state.mouthScaleY;
            mouth.scale.copy(mouthScale);

            antennaBall.material.emissiveIntensity = state.antennaGlow;
            ring.scale.setScalar(state.ringScale);
            ring.material.opacity = state.ringOpacity;

            ring.rotation.z += 0.005;
            var ringY = bodyGroup.position.y - 0.65;
            ring.position.y = ringY;
        }

        var clock = new THREE.Clock();
        var autoReturnTimer = 0;
        var AUTO_RETURN_DELAY = 3.0;

        function animate() {
            requestAnimationFrame(animate);

            var dt = Math.min(clock.getDelta(), 0.1);

            if (state.targetAction !== state.currentAction && state.targetAction !== 'level_up') {
                state.transitionProgress += dt * 3;
                if (state.transitionProgress >= 1) {
                    state.transitionProgress = 1;
                    state.currentAction = state.targetAction;
                    state.actionTimer = 0;
                }
            }

            if (state.targetAction === 'level_up' && state.actionTimer > actionConfig.level_up.duration) {
                state.currentAction = 'idle';
                state.targetAction = 'idle';
                state.transitionProgress = 0;
                state.actionTimer = 0;
                state.levelUpTime = 0;
            }

            var updater = animationUpdaters[state.currentAction];
            if (updater) {
                updater(dt);
            }

            if (state.currentAction !== 'idle' && state.currentAction !== 'level_up' && state.currentAction !== 'sleep') {
                autoReturnTimer += dt;
                if (autoReturnTimer > AUTO_RETURN_DELAY) {
                    setAction('idle');
                    autoReturnTimer = 0;
                }
            } else {
                autoReturnTimer = 0;
            }

            applyState();
            updateStarParticles();
            renderer.render(scene, camera);
        }

        function onResize() {
            var w = container.clientWidth || 180;
            var h = container.clientHeight || 160;
            camera.aspect = w / h;
            camera.updateProjectionMatrix();
            renderer.setSize(w, h);
        }

        window.addEventListener('resize', onResize);

        animate();

        return {
            setAction: setAction,
            getCurrentAction: function() { return state.currentAction; },
            getTargetAction: function() { return state.targetAction; },
            resize: onResize,
            dispose: function() {
                window.removeEventListener('resize', onResize);
                renderer.dispose();
                container.removeChild(renderer.domElement);
            }
        };
    };

    window.Avatar3D = Avatar3D;
})();