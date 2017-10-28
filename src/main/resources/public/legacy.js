'use strict';

class PaperEngine {
  constructor(canvas, url) {
    const that = this;
    this._canvas = canvas;
    this._socketUrl = url;
    this._tracks = new Map();
    this._engine = new BABYLON.Engine(canvas, true);
    this._scene = new BABYLON.Scene(this._engine);
    this._loadAssets(() => {
      that._setupEngine(canvas);
      that._setupSocket();
    });
  }

  _loadAssets(cb) {
    const manager = new BABYLON.AssetsManager(this._scene);
    manager.onFinish = cb;
    const task = manager.addMeshTask('PaperAirplane', '', 'assets/', 'paper_airplane.babylon');
    task.onError = function (error) {
      console.log(error);
    };
    task.onSuccess = function (task) {
      this._refPlane = task.loadedMeshes[0];
      this._refPlane.position.y = -10;
    }.bind(this);
    manager.load();
  }

  _setupSocket() {
    this._socket = new WebSocket(this._socketUrl);
    this._socket.onclose = this._handleSocketClose.bind(this);
    this._socket.onerror = this._handleSocketClose.bind(this);
    this._socket.onopen = this._handleSocketOpen.bind(this);
    this._socket.onmessage = this._handleSocketIncoming.bind(this);

  }

  _setupEngine(canvas) {
    this._scene.clearColor = new BABYLON.Color4(0.30, 0.30, 0.30, 1);
    this._camera = new BABYLON.ArcRotateCamera('main', 0, 0, 70, new BABYLON.Vector3(25, 0,25), this._scene);
    //this._camera = new BABYLON.TouchCamera('main', new BABYLON.Vector3(0,10,0), this._scene);
    this._camera.lowerBetaLimit = 0;
    this._camera.upperBetaLimit = (Math.PI / 2) * 0.9;
    this._camera.lowerRadiusLimit = 15;
    this._camera.upperRadiusLimit = 70;
    this._camera.attachControl(canvas, true);

    const light = new BABYLON.HemisphericLight('light', new BABYLON.Vector3(0,200,0), this._scene);
    light.specular = new BABYLON.Color3(0.1, 0.1, 0.1);

    const ground = BABYLON.MeshBuilder.CreateGround('ground', {width: 50, height: 50, subdivisions: 1}, this._scene);
    ground.position = new BABYLON.Vector3(25, 0, 25);
    const groundMaterial = new BABYLON.StandardMaterial('groundMaterial', this._scene);
    groundMaterial.diffuseColor = new BABYLON.Color3(0.25, 0.25, 0.25);
    ground.material = groundMaterial;

    this._engine.runRenderLoop(this._eventLoop.bind(this));
    window.addEventListener('resize', this._resize.bind(this));

    this._hud = BABYLON.GUI.AdvancedDynamicTexture.CreateFullscreenUI('HUD');

    this._fps = new BABYLON.GUI.TextBlock('fps', '0 FPS');
    this._fps.textHorizontalAlignment = BABYLON.GUI.Control.HORIZONTAL_ALIGNMENT_RIGHT;
    this._fps.textVerticalAlignment = BABYLON.GUI.Control.VERTICAL_ALIGNMENT_TOP;
    this._fps.fontSize = 10;
    setInterval(this._updateFps.bind(this), 1000);
    this._hud.addControl(this._fps);

    const debugStackContainer = new BABYLON.GUI.Rectangle();
    debugStackContainer.horizontalAlignment = BABYLON.GUI.Control.HORIZONTAL_ALIGNMENT_LEFT;
    debugStackContainer.verticalAlignment = BABYLON.GUI.Control.VERTICAL_ALIGNMENT_TOP;
    debugStackContainer.width = 0.5;
    debugStackContainer.thickness = 0;
    this._hud.addControl(debugStackContainer);
    const debugStack = new BABYLON.GUI.StackPanel();
    debugStack.verticalAlignment = BABYLON.GUI.Control.VERTICAL_ALIGNMENT_TOP;
    debugStackContainer.addControl(debugStack);

    this._cameraDebug = PaperEngine._addDebugInfoToStack('Camera', debugStack, 'No camera');
    this._networkDebug = PaperEngine._addDebugInfoToStack('Network', debugStack, 'Not connected');
  }

  static _addDebugInfoToStack(name, stack, info) {
    const text = new BABYLON.GUI.TextBlock(`Debug${name}`, name);
    text.fontSize = 10;
    text.height = '15px';
    text.textHorizontalAlignment = BABYLON.GUI.Control.HORIZONTAL_ALIGNMENT_LEFT;
    if (info) {
      text.text = `${name} | ${info}`;
    }
    stack.addControl(text);
    return text;
  }

  _resize() {
    this._engine.setSize(window.innerWidth, window.innerHeight);
  }

  _eventLoop() {
    this._updateCameraInfo();
    this._scene.render();
  }

  _updateCameraInfo() {
    let cameraInfo = 'Camera | ' +
      'x: ' + this._scene.activeCamera.position.x.toPrecision(3) + ' ' +
      'y: ' + this._scene.activeCamera.position.y.toPrecision(3) + ' ' +
      'z: ' + this._scene.activeCamera.position.z.toPrecision(3) + ' ';
    if (this._scene.activeCamera.alpha !== undefined &&
      this._scene.activeCamera.beta !== undefined &&
      this._scene.activeCamera.radius !== undefined) {
      cameraInfo = cameraInfo + 'a: ' + this._scene.activeCamera.alpha.toPrecision(3) + ' ' +
                    'b: ' + this._scene.activeCamera.beta.toPrecision(3) + ' ' +
                    'r: ' + this._scene.activeCamera.radius.toPrecision(3);
    }
    this._cameraDebug.text = `${cameraInfo}`;
  }

  _updateFps() {
    this._fps.text = `${Math.floor(this._engine.getFps())} FPS`;
  }

  _handleSocketOpen(event) {
    const that = this;
    if (this._retry !== undefined) {
      clearInterval(this._retry);
      this._retry = undefined;
    }
    this._tracks.forEach((track) => {
      if (track.disposeLabel !== undefined) {
        track.disposeLabel();
      }
      track.dispose();
    });
    this._tracks.clear();
    this._networkDebug.text = 'Network | ' + event.currentTarget.url;
    this._socket.send(JSON.stringify(
      {
        time: new Date().getTime(),
        type: 'subscribe'
      }
    ));
  }

  _handleSocketClose() {
    this._networkDebug.text = 'Network | Not connected';
    if (this._retry === undefined) {
      this._retry = setInterval(() => {
        this._setupSocket();
      }, 5000);
    }
  }

  _handleSocketIncoming(event) {
    const time = new Date().getTime();
    try {
      const message = JSON.parse(event.data);
      const latency = time - message.time;
      this._networkDebug.text = 'Network | ' + event.currentTarget.url + ' latency: ' + latency + ' ms';
      switch (message.type) {
        case 'instruction':
          this._handleTick(message['instructions']);
          break;
        default:
          console.log('unknown message: ' + message);
          break;
      }
    } catch (e) {
      console.log(e)
    }
  }

  _handleTick(instructions) {
    const that = this;
    instructions.forEach((i) => {
      const name = i.id;
      switch (i['action']) {
        case 'create':
          const createdMesh = this._refPlane.createInstance(name);
          this._tracks.set(name, createdMesh);
          createdMesh.domainMetadata = {
            id: i.id,
            start: i.from,
            end: i.to,
            duration: i.duration,
            direction: i.direction
          };
          createdMesh.actionManager = new BABYLON.ActionManager(this._scene);
          const tapAction = function () {
            const container = new BABYLON.GUI.StackPanel();
            container.isVertical = false;

            const plane = BABYLON.Mesh.CreatePlane(`plane-${i.id}`, 1, that._scene);
            plane.parent = createdMesh;
            plane.position.y = 20;

            that._hud.addControl(container);
            container.linkWithMesh(plane);

            const followButton = BABYLON.GUI.Button.CreateSimpleButton(`follow-${i.id}`, 'Follow');
            followButton.height = '40px';
            followButton.width = '60px';
            followButton.background = '#808080';
            followButton.onPointerDownObservable.add(function () {
              const followCamera = new BABYLON.FollowCamera(`followcam-${i.id}`, BABYLON.Vector3.Zero(), that._scene);
              followCamera.lockedTarget = createdMesh;
              followCamera.radius = 10;
              followCamera.attachControl(that._canvas, true);
              that._scene.activeCamera = followCamera;
            });
            container.addControl(followButton);

            const box = new BABYLON.GUI.Rectangle();
            box.height = '40px';
            box.width = '160px';
            box.background = '#383838';
            box.thickness = 0;
            container.addControl(box);

            const label = new BABYLON.GUI.TextBlock();
            label.textHorizontalAlignment = BABYLON.GUI.Control.HORIZONTAL_ALIGNMENT_RIGHT;
            label.fontSize = 10;
            label.text = [
              `origin: lat(${i.from['latitude']}) long(${i.from['longitude']}) alt(${i.from['altitude']})`,
              `destination: lat(${i.to['latitude']}) long(${i.to['longitude']}) alt(${i.to['altitude']})`,
              `direction: ${i.direction}`
            ].join('\n');

            createdMesh.disposeLabel = function () {
              box.removeControl(label);
              container.removeControl(box);
              container.removeControl(followButton);
              that._hud.removeControl(container);
              plane.dispose(false);
            };

            label.onPointerDownObservable.add(createdMesh.disposeLabel);
            box.addControl(label);
          };
          createdMesh.actionManager.registerAction(
            new BABYLON.ExecuteCodeAction(BABYLON.ActionManager.OnPickTrigger, tapAction)
          );
          switch (i.direction) {
            case 'N':
              createdMesh.rotation = new BABYLON.Vector3(0, 2*Math.PI, 0);
              break;
            case 'NE':
              createdMesh.rotation = new BABYLON.Vector3(0, Math.PI/4, 0);
              break;
            case 'E':
              createdMesh.rotation = new BABYLON.Vector3(0, Math.PI/2, 0);
              break;
            case 'SE':
              createdMesh.rotation = new BABYLON.Vector3(0, 3*Math.PI/4, 0);
              break;
            case 'S':
              createdMesh.rotation = new BABYLON.Vector3(0, Math.PI, 0);
              break;
            case 'SW':
              createdMesh.rotation = new BABYLON.Vector3(0, 5*Math.PI/4, 0);
              break;
            case 'W':
              createdMesh.rotation = new BABYLON.Vector3(0, 3*Math.PI/2, 0);
              break;
            case 'NW':
              createdMesh.rotation = new BABYLON.Vector3(0, 7*Math.PI/4, 0);
              break;
          }
          const frames = i.duration * 30 * 10;
          const createFrom = new BABYLON.Vector3(i.from['latitude'], i.from['altitude'], i.from['longitude']);
          const createTo = new BABYLON.Vector3(i.to['latitude'], i.to['altitude'], i.to['longitude']);
          BABYLON.Animation.CreateAndStartAnimation(
            name,
            createdMesh,
            'position',
            30,
            frames,
            createFrom,
            createTo,
            BABYLON.Animation.ANIMATIONLOOPMODE_RELATIVE
          );
          console.log(`created: ${createFrom} ${createTo} ${frames}`);
          break;
        case 'update':
          console.log('projection update is not supported.');
          break;
        case 'delete':
          const deletedTrack = this._tracks.get(i.id);
          if (deletedTrack.disposeLabel !== undefined) {
            deletedTrack.disposeLabel();
          }
          deletedTrack.dispose();
          this._tracks.delete(i.id);
          const rounded = new BABYLON.Vector3(
            Math.round(deletedTrack.position.x),
            Math.round(deletedTrack.position.y),
            Math.round(deletedTrack.position.z)
          );
          console.log(`deleted: ${rounded}`);
          break;
      }
    });
    console.log('============================');
  }

}
window.addEventListener('DOMContentLoaded', () => {
  new PaperEngine(document.getElementById('app'), 'ws://127.0.0.1:8989/a');
});
