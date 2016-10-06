package template;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;
import logist.LogistPlatform;



public class ReactiveTemplate implements ReactiveBehavior {
	
	public static void main(String[] args) {
		logist.LogistPlatform.main(args);
	}

	private final int actionTakeTask = 0 , actionMoveRandomly = 1;
	
	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private List<State> states;
	private int stateSize , nbCity ;
	
	private double T[][][], R[][] , V[]; 
	

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
		this.nbCity = topology.size();
		
		CreateStateDomain(topology);
		CreateRewardTable(topology,td);
		CreateProbabilityTable (topology , td);
		
		CreateIndicatingVector(discount);
		
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		
		State currentState ;
		City currentCity = vehicle.getCurrentCity();
		
		if ( availableTask == null) {
			currentState = new State ( vehicle.getCurrentCity()  , vehicle.getCurrentCity().randomNeighbor(random) , false) ;
		} else {
			currentState = new State ( vehicle.getCurrentCity()  , availableTask.deliveryCity , true );
		}
		
		if ( V[currentState.toInt()] == actionMoveRandomly ){
			action = new Move(currentCity.randomNeighbor(random));
		} else if (V[currentState.toInt()] == actionTakeTask) {
			action = new Pickup(availableTask);
		}
		
		if (availableTask == null || random.nextDouble() > pPickup) {
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	
	void CreateStateDomain (Topology topology) {
		stateSize = topology.size()*topology.size()*2;
		List<City> city = topology.cities();
		for (City  c1 : city){
			for(City c2 : city ) {
				states.add(new State (c1,c2,true));
				states.add(new State (c1,c2,false));
			}
		}
	}
	
	void CreateRewardTable(Topology topology ,TaskDistribution td) {
		int size = topology.size();
		R = new double[stateSize][2];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < 3; j++) {
				R[i][j]=0;
			}
		}
		List<City> cities = topology.cities();
		for(City c : cities){
			for(City neig : c.neighbors()){
				int pos = (new State(c , neig , false )).toInt() ;
				R[pos][actionMoveRandomly] =  - c.distanceTo(neig) ;
				R[pos][actionTakeTask] =  - c.distanceTo(neig) ;
			}
			for (City c2 : cities) {
				int pos = (new State(c , c2 , true )).toInt() ;
				R[pos][actionTakeTask] = td.reward(c, c2) - c.distanceTo(c2) ;
			}
		}
	}
	
	private void CreateProbabilityTable (Topology topology , TaskDistribution td){
		T = new double[nbCity][2][nbCity];
		for (int i = 0; i < nbCity; i++) {
			for (int j = 0; j < nbCity; j++) {
				T[i][0][j] = 0;
				T[i][1][j] = 0;
			}
		}
		
		List<City> cities = topology.cities();
		
		for(City c1 : cities){
			for(City c2 : cities){
				//Take task proba
				double probaCount = 1;
				for(City c3 : cities){
					double cproba = td.probability(c2, c3);
					probaCount -= cproba;
					T[(new State(c1, c2 , true)).toInt()]
							[actionTakeTask]
							[(new State(c2, c3 , true)).toInt()] = cproba;
				}
				
				List<City> neighbor = c2.neighbors();
				int nbNeighbor = neighbor.size();
				for ( City nc : neighbor) {
					T[(new State(c1, c2 , true)).toInt()]
							[actionTakeTask]
							[(new State(c2, nc , false)).toInt()] = probaCount*( 1 / nbNeighbor);
				}
				
				//random move proba
				for(City c3 : c1.neighbors()){
					T[(new State(c1, c2 , true)).toInt()]
							[actionMoveRandomly]
							[(new State(c1, c3 , false)).toInt()] = 1/((double)c1.neighbors().size());
				}
				
				
				
				
			}
			
			for(City c2 : c1.neighbors()){
				double probaCount = 1;
				for(City c3 : cities){
					double cproba = td.probability(c2, c3);
					probaCount -= cproba;
					T[(new State(c1, c2 , false)).toInt()]
							[actionMoveRandomly]
							[(new State(c2, c3 , true)).toInt()] = cproba;
					
				}
				
				List<City> neighbor = c2.neighbors();
				int nbNeighbor = neighbor.size();
				for ( City nc : neighbor) {
					T[(new State(c1, c2 , false)).toInt()]
							[actionMoveRandomly]
							[(new State(c2, nc , false)).toInt()] = probaCount*( 1 / nbNeighbor);
				}
			}
		}
	}
	
	private void CreateIndicatingVector(double discount) {
		int noDiff = 0 ;
		double Qmax[] = new double[stateSize] ;
		for (int i = 0; i < stateSize; i++) {
			Qmax[i] = 0 ;
		}
		while ( noDiff < stateSize){
			noDiff=0;
			
			for(State s : states){
				
				int coord = s.toInt();
				
				double Q[]= new double[2];
				
				//liste action
				for (int a = 0; a < 2; a++) {
					Q[a]= R[coord][a];
					for (State s2 : states) {
						Q[a] += discount* T[coord][a][s2.toInt()]*Qmax[coord];
					}
					
					int best ; 
					if(Q[0]<Q[1]){
						best = 1;
					}else {
						best = 0;
					}
					
					V[coord] = best ;
					if(Qmax[coord] == Q[best]){
						noDiff++;
					}
					Qmax[coord] = Q[best];
					
				}
			}
			
		}
	}
	
	private class State {
		public City currentCityId;
		public City toCityId ;
		public boolean takeTask;
		
		public State (City ccid , City tcid , boolean tt ) {
			currentCityId = ccid ;
			toCityId = tcid ;
			takeTask = tt ;
		}
		
		public int toInt(){
			if (takeTask){
				return currentCityId.id *nbCity*2+toCityId.id*2;
			} else {
				return currentCityId.id *nbCity*2+toCityId.id*2+1;
			}
		}
	}
}


