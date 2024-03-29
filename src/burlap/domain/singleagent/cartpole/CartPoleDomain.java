package burlap.domain.singleagent.cartpole;

import burlap.oomdp.auxiliary.DomainGenerator;
import burlap.oomdp.core.Attribute;
import burlap.oomdp.core.Domain;
import burlap.oomdp.core.ObjectClass;
import burlap.oomdp.core.ObjectInstance;
import burlap.oomdp.core.State;
import burlap.oomdp.core.TerminalFunction;
import burlap.oomdp.singleagent.Action;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.RewardFunction;
import burlap.oomdp.singleagent.SADomain;
import burlap.oomdp.singleagent.explorer.VisualExplorer;


/**
 * The classic cart pole balancing problem as described by Barto, Sutton, and Anderson [2] with correct mechanics as described by Florian [1]. 
 * The agent controls a cart that can apply horizontal force
 * in either direction. Attached to the cart is a pole on a hinge and the goal of the agent is to apply force to the cart such that
 * the pole stays vertically balanced. If the angle between the pole and the vertical axis is greater than
 * some threshold (originally 12 degrees or about 0.2 radians), the agent fails. The track on which the cart can move is also finite in size,
 * and running into the end of the track is also considered failure; however, the track can be set to infinite by setting the {@link #isFiniteTrack}
 * parameter to false. The infinte track is handled by never changing the position value of the cart.
 * <p/>
 * By default, this implementation will use the simulation described by Florian, which corrects two problems in the classic Barto, Sutton, and Anderson paper.
 * The two problems were (1) gravity was specified as negative in the equations when it should have been positive and (2) friction was not calculated
 * correctly. However, this domain may also be set to use the classic incorrect mechanics or the classic mechanics with correct gravity for comparison
 * purposes. To do so, use the methods {@link #setToIncorrectClassicModel()} and {@link #setToIncorrectClassicModelWithCorrectGravity()}. Note that
 * when incorrect gravity is used, the pole will "bounce" once it reaches about 90 degrees (though in most tasks the pole is never allowed to fall this far).
 * <p/>
 * This domain consists of a single object with 4 real valued attributes: the x position of the cart, the x velocity of the cart, the angle between the pole
 * and the vertical axis, and the speed of the change in angle. Additionally, a 5th hidden attribute is included
 * when the corrected physics are used that maintains the sign of the normal force in the last step. If the classic mechanics are used instead,
 * then this hidden attribute is not included.
 * The physics are simulated using a non-linear differential equation
 * that is estimated using Euler's Method. All system parmeters are defaulted to those used in the
 * original paper, but they may modified as deisred.
 * <p/>
 * Also included with this class are default classes for reward function and terminal function for this domain.
 * <p/>
 * Running the main method of this class will launch and interactive visualizer with the 'a' and 'd' keys controlling left and right movement
 * force respectively.

 * 
 * 1. Florian, Razvan V. "Correct equations for the dynamics of the cart-pole system." Center for Cognitive and Neural Studies (Coneural), Romania (2007).
 * 2. Barto, Andrew G., Richard S. Sutton, and Charles W. Anderson. "Neuronlike adaptive elements that can solve difficult learning control problems." 
 * Systems, Man and Cybernetics, IEEE Transactions on 5 (1983): 834-846.
 * 
 * @author James MacGlashan
 *
 */
public class CartPoleDomain implements DomainGenerator {

	/**
	 * A constant for the name of the position attribute
	 */
	public static final String				ATTX = "xAtt";
	
	/**
	 * A constant for the name of the position velocity
	 */
	public static final String				ATTV = "xvAtt";
	
	/**
	 * A constant for the name of the angle attribute
	 */
	public static final String				ATTANGLE = "angleAtt";
	
	/**
	 * A constant for the name of the angle velocity
	 */
	public static final String				ATTANGLEV = "angleVAtt";
	

	/**
	 * Attribute name for maintaining the direction sign of the force normal.
	 * This attribute will only be included if the correct model is being used.
	 */
	public static final String				ATTNORMSGN = "normalSign";
	
	/**
	 * A constant for the name of the cart and pole object to be moved
	 */
	public static final String				CLASSCARTPOLE = "cartPole";
	
	/**
	 * A constant for the name of the left action
	 */
	public static final String				ACTIONLEFT = "left";
	
	/**
	 * A constant for the name of the right action
	 */
	public static final String				ACTIONRIGHT = "right";
	
	
	
	/*
	 * The half length size of the track on which the cart moves.
	 */
	public double							halfTrackLength = 2.4;
	
	/**
	 * The maximimum radius the pole can fall. Note, physics get weird and non-realisitc at pi/2;
	 * task should terminate before then.
	 */
	public double							angleRange = Math.PI/2;
	
	
	/**
	 * the force of gravity; should be *positive* for the correct mechanics.
	 */
	public double							gravity = 9.8;
	
	/**
	 * The mass of the cart.
	 */
	public double							cartMass = 1.;
	
	/**
	 * The mass of the pole.
	 */
	public double							poleMass = .1;
	
	/**
	 * The half length of the pole.
	 */
	public double							halfPoleLength = 0.5;
	
	/**
	 * The friction between the cart and ground
	 */
	public double							cartFriction = 0.0005;
	
	/**
	 * The friction between the pole and the joint on the cart.
	 */
	public double							poleFriction = 0.000002;
	
	/**
	 * The force magnatude that can be exterted in either direction on the cart
	 */
	public double							movementForceMag = 10.;
	
	
	/**
	 * The time between each action selection
	 */
	public double							timeDelta = 0.02;
	
	/**
	 * The maximum speed of the cart. A good place to set it to is somewhere around 
	 * the max expected if the cart was pushed from one side of the track to the other.
	 * The default is 6.81 (which is an upperbound of that with default parameters)
	 */
	public double							maxCartSpeed = 6.81;
	
	/**
	 * The maximum speed of the change in angle. The default sets it to the speed that would result
	 * in 12 degress for a sinlge time step of 0.02 (which is about 10.47 radians/second), since 12 degrees
	 * is the default termination range.
	 */
	public double							maxAngleSpeed = 10.47; //12 degrees per time step of 0.02 seconds
	
	
	/**
	 * Whether the track is finite (true) or infinite (false). When the track is infinite, the position of the cart always remains the same.
	 */
	public boolean							isFiniteTrack = true;
	
	
	/**
	 * Specifies whether the correct Cart Pole physical model should be used or the classic, but incorrect, Barto Sutton and Anderson model [1].
	 */
	protected boolean 						useCorrectModel = true;
	
	
	
	@Override
	public Domain generateDomain() {
		
		SADomain domain = new SADomain();
		
		Attribute xatt = new Attribute(domain, ATTX, Attribute.AttributeType.REAL);
		xatt.setLims(-halfTrackLength, halfTrackLength);
		
		Attribute xvatt = new Attribute(domain, ATTV, Attribute.AttributeType.REAL);
		xvatt.setLims(-this.maxCartSpeed, this.maxCartSpeed);
		
		Attribute angleatt = new Attribute(domain, ATTANGLE, Attribute.AttributeType.REAL);
		angleatt.setLims(-this.angleRange, this.angleRange);
		
		Attribute anglevatt = new Attribute(domain, ATTANGLEV, Attribute.AttributeType.REAL);
		anglevatt.setLims(-this.maxAngleSpeed, this.maxAngleSpeed);
		
		Attribute normAtt = null;
		if(this.useCorrectModel){
			normAtt = new Attribute(domain, ATTNORMSGN, Attribute.AttributeType.REAL);
			normAtt.setLims(-1., 1.);
			normAtt.hidden = true;
			
		}
		
		ObjectClass cartPoleClass = new ObjectClass(domain, CLASSCARTPOLE);
		cartPoleClass.addAttribute(xatt);
		cartPoleClass.addAttribute(xvatt);
		cartPoleClass.addAttribute(angleatt);
		cartPoleClass.addAttribute(anglevatt);
		if(this.useCorrectModel){
			cartPoleClass.addAttribute(normAtt);
		}
		
		new MovementAction(ACTIONLEFT, domain, -1.);
		new MovementAction(ACTIONRIGHT, domain, 1.);
		
		
		return domain;
	}
	
	/**
	 * Sets to use the classic model by Barto, Sutton, and Anderson which has incorrect friction forces, but will use
	 * correct gravity.
	 */
	public void setToIncorrectClassicModelWithCorrectGravity(){
		this.gravity = Math.abs(this.gravity);
		this.useCorrectModel = false;
	}
	
	/**
	 * Sets to the use the classic model by Barto, Sutton, and Anderson, which has incorrect friction forces and gravity
	 * in the wrong direction
	 */
	public void setToIncorrectClassicModel(){
		this.gravity = Math.abs(this.gravity)*-1;
		this.useCorrectModel = false;
	}
	
	
	/**
	 * Sets to use the correct physics model by Florian.
	 */
	public void setToCorrectModel(){
		this.gravity = Math.abs(this.gravity);
		this.useCorrectModel = true;
	}
	
	/**
	 * Given the current action force, track length and masses, sets the max cart speed 
	 * to an upperbound of what is possible from moving from one side of the track to another.
	 * This method modifies the parameter maxCartSpeed.
	 * @return the resulting max speed that is set.
	 */
	public double setMaxCartSpeedToMaxWithMovementFromOneSideToOther(){
		
		//using simplified mechanics
		double cartAcceleration = this.movementForceMag / (this.cartMass + this.poleMass);
		
		//time to go from one end to the other
		double t = Math.sqrt(2 * (2*this.halfTrackLength) / cartAcceleration);
		
		//final time
		double vf = cartAcceleration * t;
		
		return vf;
	}
	
	
	/**
	 * Returns the default initial state: the cart centered on the track, not moving, with the pole perfectly vertical.
	 * @param domain the domain object to which the state will be associated.
	 * @return an initial task state.
	 */
	public static State getInitialState(Domain domain){
		return getInitialState(domain, 0., 0., 0., 0.);
	}
	
	
	/**
	 * Returns an initial state with the given initial values for the cart and pole.
	 * @param domain the domain object to which the state will be associated.
	 * @param x the position of cart.
	 * @param xv the velocity of the cart.
	 * @param a the angle between the pole and the vertical axis
	 * @param av the velocity of the angle
	 * @return the corresponding initial state object
	 */
	public static State getInitialState(Domain domain, double x, double xv, double a, double av){
		ObjectInstance cartPole = new ObjectInstance(domain.getObjectClass(CLASSCARTPOLE), CLASSCARTPOLE);
		cartPole.setValue(ATTX, x);
		cartPole.setValue(ATTV, xv);
		cartPole.setValue(ATTANGLE, a);
		cartPole.setValue(ATTANGLEV, av);
		if(domain.getAttribute(ATTNORMSGN) != null){
			cartPole.setValue(ATTNORMSGN, 1.);
		}
		
		State s = new State();
		s.addObject(cartPole);
		
		return s;
	}
	
	
	/**
	 * Simulates the physics for one time step give the input state s, and the direction of force applied. The input state will be directly
	 * modified to be the next state. Physics simulated using one step of Euler's method on the non-linear differential equations provided by Barto
	 * Sutton, and Anderson [2]. <b>Note that this model is not physically correct [1] but is left in code for historical comparisons</b>. Optionally a
	 * correct model can be used instead.
	 * @param s the current state from which one time step of physics will be simulated.
	 * @param dir the direction of force applied; should be -1, or 1 and is multiplied to this objects movementForceMag parameter. 0 would cause no force.
	 * @return the input state, which has been modified to the next state after one time step of simulation.
	 */
	public State moveClassicModel(State s, double dir){
		
		ObjectInstance cartPole = s.getFirstObjectOfClass(CLASSCARTPOLE);
		double x0 = cartPole.getRealValForAttribute(ATTX);
		double xv0 = cartPole.getRealValForAttribute(ATTV);
		double a0 = cartPole.getRealValForAttribute(ATTANGLE);
		double av0 = cartPole.getRealValForAttribute(ATTANGLEV);
		
		double f = dir * this.movementForceMag;
		
		double sMass = this.cartMass + this.poleMass;
		
		
		
		//compute second derivatives (x_2 and a_2) at current point
		double anumCosFactor = (-f 
									- (this.poleMass*this.halfPoleLength*av0*av0*Math.sin(a0)) 
									+ (this.cartFriction * Math.signum(xv0))
								) / sMass;
		double anumPFricTerm = (this.poleFriction*av0) / (this.poleMass*this.halfPoleLength);
		
		double anum = (this.gravity * Math.sin(a0)) 
						+ (Math.cos(a0) * anumCosFactor) 
						- anumPFricTerm;
		
		double adenom = this.halfPoleLength 
								* ( (4./3.) 
										- ((this.poleMass*Math.pow(Math.cos(a0), 2.)) / sMass) 
								  );
		
		double a_2 = anum / adenom;
		
		double xnum = f 
						+ this.poleMass*this.halfPoleLength*(av0*av0*Math.sin(a0) 
																- a_2 * Math.cos(a0)) 
						- (this.cartFriction*Math.signum(xv0));
		
		double x_2 = xnum / sMass;
		
		
		//perform Euler's method
		double xf = x0 + this.timeDelta * xv0;
		double xvf = xv0 + this.timeDelta*x_2;
		
		double af = a0 + this.timeDelta*av0;
		double avf = av0 + this.timeDelta*a_2;
		
		
		
		//clamp values
		if(Math.abs(xf) > this.halfTrackLength){
			xf = Math.signum(xf)*this.halfTrackLength;
			xvf = 0.;
		}
		
		if(Math.abs(xvf) > this.maxCartSpeed){
			xvf = Math.signum(xvf) * this.maxCartSpeed;
		}
		
		if(Math.abs(af) > this.angleRange){
			af = Math.signum(af) * this.angleRange;
			avf = 0.;
		}
		
		if(Math.abs(avf) > this.maxAngleSpeed){
			avf = Math.signum(avf) * this.maxAngleSpeed;
		}
		
		
		//set new values
		if(this.isFiniteTrack){
			cartPole.setValue(ATTX, xf);
		}
		cartPole.setValue(ATTV, xvf);
		cartPole.setValue(ATTANGLE, af);
		cartPole.setValue(ATTANGLEV, avf);
		
		
		return s;
		
	}
	
	
	/**
	 * Simulates the physics for one time step give the input state s, and the direction of force applied. The input state will be directly
	 * modified to be the next state. Physics simulated using one step of Euler's method on the corrected non-linear differential equations [1].
	 * @param s the current state from which one time step of physics will be simulated.
	 * @param dir the direction of force applied; should be -1, or 1 and is multiplied to this objects movementForceMag parameter. 0 would cause no force.
	 * @return the input state, which has been modified to the next state after one time step of simulation.
	 */
	public State moveCorrectModel(State s, double dir){
		
		ObjectInstance cartPole = s.getFirstObjectOfClass(CLASSCARTPOLE);
		double x0 = cartPole.getRealValForAttribute(ATTX);
		double xv0 = cartPole.getRealValForAttribute(ATTV);
		double a0 = cartPole.getRealValForAttribute(ATTANGLE);
		double av0 = cartPole.getRealValForAttribute(ATTANGLEV);
		double nsgn0 = cartPole.getRealValForAttribute(ATTNORMSGN);
		
		double f = dir * this.movementForceMag;
		
		double a_2 = this.getAngle2ndDeriv(xv0, a0, av0, nsgn0, f);
		double n = this.getNormForce(a0, av0, a_2);
		double nsgnf = Math.signum(n);
		if(nsgnf != nsgn0){
			a_2 = this.getAngle2ndDeriv(xv0, a0, av0, nsgnf, f);
		}
		double x_2 = this.getX2ndDeriv(xv0, a0, av0, n, f, a_2);
		
		//perform Euler's method
		double xf = x0 + this.timeDelta * xv0;
		double xvf = xv0 + this.timeDelta*x_2;
		
		double af = a0 + this.timeDelta*av0;
		double avf = av0 + this.timeDelta*a_2;
		
		
		
		//clamp values
		if(Math.abs(xf) > this.halfTrackLength){
			xf = Math.signum(xf)*this.halfTrackLength;
			xvf = 0.;
		}
		
		if(Math.abs(xvf) > this.maxCartSpeed){
			xvf = Math.signum(xvf) * this.maxCartSpeed;
		}
		
		if(Math.abs(af) > this.angleRange){
			af = Math.signum(af) * this.angleRange;
			avf = 0.;
		}
		
		if(Math.abs(avf) > this.maxAngleSpeed){
			avf = Math.signum(avf) * this.maxAngleSpeed;
		}
		
		
		//set new values
		if(this.isFiniteTrack){
			cartPole.setValue(ATTX, xf);
		}
		cartPole.setValue(ATTV, xvf);
		cartPole.setValue(ATTANGLE, af);
		cartPole.setValue(ATTANGLEV, avf);
		cartPole.setValue(ATTNORMSGN, n);
		
		
		
		return s;
	}
	
	/**
	 * Computes the 2nd order derivative of the angle for a given normal force sign using the corrected model.
	 * @param xv0 the cart velocity
	 * @param a0 the pole angle
	 * @param av0 the pole angle velocity
	 * @param nsign the normal force sign
	 * @param f the force applied to the cart
	 * @return the 2nd order derivative of the angle
	 */
	protected double getAngle2ndDeriv(double xv0, double a0, double av0, double nsign, double f){
		
		double sMass = this.cartMass + this.poleMass;
		
		double sint = Math.sin(a0);
		double cost = Math.cos(a0);
		
		double anumCosFactor = (-f 
				- (this.poleMass*this.halfPoleLength*av0*av0
						* (
								sint + this.cartFriction*Math.signum(nsign*xv0)*cost
						  )
				  )
				
			) / sMass;
		
		double anumPFricTerm = this.cartFriction*this.gravity*Math.signum(nsign*xv0);
		
		double anum = (this.gravity * Math.sin(a0)) 
				+ (Math.cos(a0) * anumCosFactor) 
				+ anumPFricTerm;
		
		double adenom = this.halfPoleLength 
				* ( (4./3.) 
						- (
							(this.poleMass*cost / sMass)
							* (cost - this.cartMass * Math.signum(nsign*xv0))
						  )
				  );
		
		
		return anum / adenom;
		
	}
	
	/**
	 * Computes the normal force for the corrected model
	 * @param a0 the pole angle
	 * @param av0 the pole angle velocity
	 * @param a_2 the 2nd order derivative of the pole angle
	 * @return the normal force
	 */
	protected double getNormForce(double a0, double av0, double a_2){
		double norm = ((this.cartMass + this.poleMass) * this.gravity)
						- (this.poleMass * this.halfPoleLength
								* (a_2 * Math.sin(a0) + (av0*av0*Math.cos(a0)))
						  );
		return norm;
	}
	
	/**
	 * Returns the second order x position derivative for the corrected model.
	 * @param xv0 the cart velocity
	 * @param a0 the pole angle
	 * @param av0 the pole angle velocity
	 * @param n the normal force
	 * @param f the force applied to the cart
	 * @param a2 the second order angle derivative
	 * @return the second order x position derivative
	 */
	protected double getX2ndDeriv(double xv0, double a0, double av0, double n, double f, double a2){
		
		double sMass = this.cartMass + this.poleMass;
		
		double sint = Math.sin(a0);
		double cost = Math.cos(a0);
		
		double xnum = f 
						+ (this.poleMass * this.halfPoleLength 
								* ((av0*av0*sint) - (a2*cost))
						  )
						- (this.cartFriction * n * Math.signum(n*xv0));
		
		double x_2 = xnum/sMass;
		
		return x_2;
		
	}
	
	
	
	
	/**
	 * A movement action which applies force in the specified direction.
	 * @author James MacGlashan
	 *
	 */
	protected class MovementAction extends Action{
		
		
		/**
		 * The direction of force that this action applies
		 */
		double dir;
		
		/**
		 * Initializes.
		 * @param name the name of the action.
		 * @param domain the domain object to which this action will be associated.
		 * @param dir the direction of force applied to the cart.
		 */
		public MovementAction(String name, Domain domain, double dir){
			super(name, domain, "");
			this.dir = dir;
		}

		@Override
		protected State performActionHelper(State s, String[] params) {
			if(CartPoleDomain.this.useCorrectModel){
				return CartPoleDomain.this.moveCorrectModel(s, this.dir);
			}
			return CartPoleDomain.this.moveClassicModel(s, this.dir);
		}
		
		
		
	}
	
	
	/**
	 * A default terminal function for this domain. Terminates when cart reaches end of track of
	 * angle between pole and vertical axis is greater than 12 degrees (about 0.2 radians).
	 * @author James MacGlashan
	 *
	 */
	public static class CartPoleTerminalFunction implements TerminalFunction{

		
		/**
		 * The maximum pole angle to cause termination/failure.
		 */
		double maxAbsoluteAngle = 12. * (Math.PI / 180.);
		
		
		/**
		 * Initializes with default max angle of 12 degrees (about 0.2 radians)
		 */
		public CartPoleTerminalFunction(){
			//do nothing
		}
		
		
		/**
		 * Initializes with a max pole angle as specified in radians
		 * @param maxAbsoluteAngleInRadians the maximum pole angle that causes task termination/failure.
		 */
		public CartPoleTerminalFunction(double maxAbsoluteAngleInRadians){
			this.maxAbsoluteAngle = maxAbsoluteAngleInRadians;
		}
		
		@Override
		public boolean isTerminal(State s) {
			
			ObjectInstance cartpole = s.getFirstObjectOfClass(CLASSCARTPOLE);
			double x = cartpole.getRealValForAttribute(ATTX);
			Attribute xatt = cartpole.getObjectClass().getAttribute(ATTX);
			double xmin = xatt.lowerLim;
			double xmax = xatt.upperLim;
			
			if(x <= xmin || x >= xmax){
				return true;
			}
			
			double a = cartpole.getRealValForAttribute(ATTANGLE);
			if(Math.abs(a) >= maxAbsoluteAngle){
				return true;
			}
			
			return false;
		}
		
	}
	
	
	/**
	 * A default reward function for this task. Returns 0 everywhere except at fail conditions, which return -1 and
	 * are defined by the agent reaching the end of the track or by the angle of the pole being grater than some threshold (default 12 degrees or about 0.2 radians).
	 * @author James MacGlashan
	 *
	 */
	public static class CartPoleRewardFunction implements RewardFunction{

		
		/**
		 * The maximum pole angle to cause failure.
		 */
		double maxAbsoluteAngle = 12. * (Math.PI / 180.);
		
		
		/**
		 * Initializes with max pole angle threshold of 12 degrees (about 0.2 radians)
		 */
		public CartPoleRewardFunction(){
			//do nothing
		}
		
		
		/**
		 * Initializes with a max pole angle as specified in radians
		 * @param maxAbsoluteAngleInRadians the maximum pole angle that causes task failure.
		 */
		public CartPoleRewardFunction(double maxAbsoluteAngleInRadians){
			this.maxAbsoluteAngle = maxAbsoluteAngleInRadians;
		}
		
		
		@Override
		public double reward(State s, GroundedAction a, State sprime) {
			
			ObjectInstance cartpole = sprime.getFirstObjectOfClass(CLASSCARTPOLE);
			double x = cartpole.getRealValForAttribute(ATTX);
			Attribute xatt = cartpole.getObjectClass().getAttribute(ATTX);
			double xmin = xatt.lowerLim;
			double xmax = xatt.upperLim;
			
			double failReward = -1;
			
			if(x <= xmin || x >= xmax){
				return failReward;
			}
			
			double ang = cartpole.getRealValForAttribute(ATTANGLE);
			if(Math.abs(ang) >= maxAbsoluteAngle){
				return failReward;
			}
			
			
			return 0.;
		}
		
		
		
	}
	
	
	/**
	 * Launches an interactive visualize in which key 'a' applies a force in the left direction and key 'd' applies force in the right direction.
	 * The corrected physics model is used.
	 * @param args ignored.
	 */
	public static void main(String [] args){
		CartPoleDomain dgen = new CartPoleDomain();
		Domain domain = dgen.generateDomain();
		
		State s = CartPoleDomain.getInitialState(domain);
		
		VisualExplorer exp = new VisualExplorer(domain, CartPoleVisualizer.getCartPoleVisualizer(), s);
		exp.addKeyAction("a", ACTIONLEFT);
		exp.addKeyAction("d", ACTIONRIGHT);
		
		exp.initGUI();
		
	}

}
