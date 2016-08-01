package com.toprace.game;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.Collision;
import com.badlogic.gdx.physics.bullet.collision.ContactListener;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btConeShape;
import com.badlogic.gdx.physics.bullet.collision.btCylinderShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody.btRigidBodyConstructionInfo;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.Disposable;

public class TopRaceGame extends ApplicationAdapter {
        final static short GROUND_FLAG = 1<<8;
        final static short OBJECT_FLAG = 1<<9;
        final static short ALL_FLAG = -1;
	public PerspectiveCamera cam;
        public ModelBatch modelBatch;
        public Environment environment;
        public CameraInputController camController;
        //public AssetManager assets;
        //public boolean loading;
        public Model model;
        public boolean collision;
        public btCollisionConfiguration collisionConfig;
        public btDispatcher dispatcher;
        Array<GameObject> instances;
        ArrayMap<String, GameObject.Constructor> constructors;
        float spawnTimer;
        btBroadphaseInterface broadphase;
        btDynamicsWorld dynamicsWorld;
        btConstraintSolver constraintSolver;
        int ObjectCounter;
        boolean pause = false;
        public FPSLogger fpslogger;
        float angle, speed = 90f;
        public AssetManager assets; 
        boolean loading;
        Matrix4 OriginCarTransform;
        
        
        /*class MyContactListener extends ContactListener {
                @Override
                public boolean onContactAdded (int userValue0, int partId0, int index0, boolean match0, int userValue1, int partId1, int index1, boolean match1) {
                    if (match0)
                    {
                        for (GameObject obj : instances)
                        {
                            if(obj.body.getUserValue()==userValue0)
                            {
                                ((ColorAttribute)obj.materials.get(0).get(ColorAttribute.Diffuse)).color.set(Color.WHITE);
                            }
                        }
                    }
                    
                    if (match1)
                    {
                        for (GameObject obj : instances)
                        {
                            if(obj.body.getUserValue()==userValue1)
                            {
                                ((ColorAttribute)obj.materials.get(0).get(ColorAttribute.Diffuse)).color.set(Color.WHITE);
                            }
                        }
                    }
                    return true;
            }
        }
        
        MyContactListener contactListener;*/
        
        static class GameObject extends ModelInstance implements Disposable {
            public final btRigidBody body;
            public final MyMotionState motionState;
            
            public GameObject(Model model, String node, btRigidBodyConstructionInfo constructionInfo) {
                super(model);
                motionState = new MyMotionState();
                motionState.transform = transform;
                if(constructionInfo != null){
                    body = new btRigidBody(constructionInfo);
                    body.setMotionState(motionState);
                }
                else
                {
                    body = null;
                }
            }
            
            @Override
            public boolean equals(Object object)
            {
                return object instanceof GameObject && ((GameObject)object).body == this.body && ((GameObject)object).motionState == this.motionState;
            }

            @Override
            public void dispose () {
                body.dispose();
                motionState.dispose();
            }
            
            static class Constructor implements Disposable {
                public final Model model;
                public final String node;
                public final btCollisionShape shape;
                public final btRigidBody.btRigidBodyConstructionInfo constructionInfo;
                private static Vector3 localInertia = new Vector3();
                
                public Constructor(Model model, String node, btCollisionShape shape, float mass) {
                    this.model = model;
                    this.node = node;
                    this.shape = shape;
                    if(mass > 0)
                        shape.calculateLocalInertia(mass, localInertia);
                    else
                        localInertia.set(0,0,0);
                    this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, localInertia);
                }

                public GameObject construct() {
                    return new GameObject(model, node, constructionInfo);
                }

                @Override
                public void dispose () {
                    shape.dispose();
                    constructionInfo.dispose();
                }
            }
        }
        
        static class MyMotionState extends btMotionState {
            Matrix4 transform;
            @Override
            public void getWorldTransform (Matrix4 worldTrans) {
                worldTrans.set(transform);
            }
            @Override
            public void setWorldTransform (Matrix4 worldTrans) {
                transform.set(worldTrans);
            }
        }

	@Override
	public void create () {
            Bullet.init();
            
            environment = new Environment();
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
            environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
            
            modelBatch = new ModelBatch();
            
            cam = new PerspectiveCamera(90, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            cam.position.set(0f, 30f, 0f);
            cam.lookAt(0f, 0f, 0f);
            cam.near = 1f;
            cam.far = 300f;
            cam.update();
            
            //camController = new CameraInputController(cam);
            //Gdx.input.setInputProcessor(camController);
            
            ModelBuilder mb = new ModelBuilder();
            mb.begin();
            mb.node().id = "ground";
            mb.part("ground", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, new Material(ColorAttribute.createDiffuse(Color.GRAY)))
                .box(50f, 1f, 50f);
            model = mb.end();

            constructors = new ArrayMap<String, GameObject.Constructor>(String.class, GameObject.Constructor.class);
            constructors.put("ground", new GameObject.Constructor(model, "ground", new btBoxShape(new Vector3(25f, 0.5f, 25f)),0f));
            
            collisionConfig = new btDefaultCollisionConfiguration();
            dispatcher = new btCollisionDispatcher(collisionConfig);
            broadphase = new btDbvtBroadphase();
            constraintSolver = new btSequentialImpulseConstraintSolver();
            dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfig);
            dynamicsWorld.setGravity(new Vector3(0, -10f, 0));
            //contactListener = new MyContactListener();
            
            ObjectCounter = 0;
            
            instances = new Array<GameObject>();
            instances.ordered=false;
            GameObject object = constructors.get("ground").construct();
            object.body.setUserValue(ObjectCounter);
            instances.add(object);
            dynamicsWorld.addRigidBody(object.body);
            object.body.setContactCallbackFlag(GROUND_FLAG);
            object.body.setContactCallbackFilter(0);
            
            fpslogger = new FPSLogger();
            
            assets = new AssetManager();
            assets.load("data/car.obj", Model.class);
            loading = true;
	}
        
        private void doneLoading() {
            ObjectCounter++;
            Model car = assets.get("data/car.obj", Model.class);
            
            constructors.put("car", new GameObject.Constructor(car, "car", new btBoxShape(new Vector3(2f, 1.5f, 2f)),1f));
            
            GameObject obj = constructors.get("car").construct();
 
            obj.transform.trn(0f, 5f, 0f);
            obj.body.proceedToTransform(obj.transform);
            obj.body.setUserValue(ObjectCounter);
            obj.body.setCollisionFlags(obj.body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK);
            
            instances.add(obj);
            dynamicsWorld.addRigidBody(obj.body);
            obj.body.setContactCallbackFlag(OBJECT_FLAG);
            obj.body.setContactCallbackFilter(GROUND_FLAG);
            
            OriginCarTransform = obj.transform;
            
            loading = false;
            System.out.format("DoneLoading !\n");
        }

	@Override
	public void render () {

            fpslogger.log();
            
            if (loading && assets.update()){
                doneLoading();
            }
            
            if(!loading)
            {
            
                if (Gdx.input.isKeyPressed(Keys.UP)) {
                    Vector3 Force = new Vector3(0f,0f,-1f);
                    Matrix4 worldTrans = new Matrix4();
                    instances.get(1).body.getMotionState().getWorldTransform(worldTrans);
                    Matrix3 temp = new Matrix3();
                    temp.set(worldTrans);
                    Vector3 correctedForce = Force.mul(temp);
                    instances.get(1).body.applyCentralImpulse(correctedForce);
                }

                if (Gdx.input.isKeyPressed(Keys.DOWN)) {
                    Vector3 Force = new Vector3(0f,0f,1f);
                    Matrix4 worldTrans = new Matrix4();
                    instances.get(1).body.getMotionState().getWorldTransform(worldTrans);
                    Matrix3 temp = new Matrix3();
                    temp.set(worldTrans);
                    Vector3 correctedForce = Force.mul(temp);
                    instances.get(1).body.applyCentralImpulse(correctedForce);
                }

                if (Gdx.input.isKeyPressed(Keys.LEFT)) {
                    instances.get(1).transform.rotate(Vector3.Y, 1f);
                    instances.get(1).body.proceedToTransform(instances.get(1).transform);
                }
                
                if (Gdx.input.isKeyPressed(Keys.RIGHT)) {
                    instances.get(1).transform.rotate(Vector3.Y, -1f);
                    instances.get(1).body.proceedToTransform(instances.get(1).transform);
                }

                Vector3 CarPosition = new Vector3();
                instances.get(1).body.getWorldTransform().getTranslation(CarPosition);
                if(CarPosition.y < -5)
                {
                    instances.get(1).body.clearForces();
                    instances.get(1).transform = OriginCarTransform;
                    instances.get(1).body.proceedToTransform(instances.get(1).transform);
                }
                
                //instances.get(1).body.getTotalForce()
            }

            final float delta = Math.min(1f/30f, Gdx.graphics.getDeltaTime());              

            dynamicsWorld.stepSimulation(delta, 5, 1f/60f);

            //camController.update();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            modelBatch.begin(cam);
            modelBatch.render(instances, environment);
            modelBatch.end();
	}    
        
        @Override
        public void dispose() {
            for (GameObject obj : instances){
                obj.dispose();
            instances.clear();
            }

            for (GameObject.Constructor ctor : constructors.values()){
                ctor.dispose();
                constructors.clear();
            }

            dispatcher.dispose();
            collisionConfig.dispose();
            
            dynamicsWorld.dispose();
            constraintSolver.dispose();
            broadphase.dispose();

            modelBatch.dispose();
            model.dispose();
            
            //contactListener.dispose();
            assets.dispose();
        }
        
        @Override
        public void resume() {
            
        }
        
        @Override
        public void resize(int width, int height) {
            
        }
        
        @Override
        public void pause() {
            
        }
}
