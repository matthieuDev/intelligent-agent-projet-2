package template;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import cern.colt.list.IntArrayList;
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

	private final int actionTakeTask = 0 ;
	
	
	
	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private List<State> states = new ArrayList<State>(0);
	private int stateSize , nbCity , nbAction ;
	private double costByKm ;
	
	
	private double T[][][], R[][] ;
	private int V[];
	private List<City > listCity ;
	

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);
		costByKm = agent.readProperty( "cost-per-km", Double.class, 0.5).doubleValue();
		

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
		this.nbCity = topology.size();
		
		nbAction = nbCity + 1 ;
		
		CreateStateDomain(topology);
		CreateRewardTable(topology,td);
		CreateProbabilityTable (topology , td);
		
		CreateIndicatingVector();
		listCity = topology.cities();
		
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		
		Action action = null;
		State currentState ;
		City currentCity = vehicle.getCurrentCity();
		if ( availableTask == null) {
			System.out.println("zut");
			currentState = new State ( currentCity  , vehicle.getCurrentCity() , false) ;
		} else {
			currentState = new State ( currentCity , availableTask.deliveryCity , true );
			
		}
		
		int intAction = V[currentState.toInt()];
		System.out.println(intAction);
		if ( intAction == actionTakeTask) {
			action = new Pickup(availableTask);
		} else {
			action = new Move(cityFromAction(intAction));
		}
		
	/*	if (availableTask == null || random.nextDouble() > pPickup) {
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}*/
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		System.out.println("1");
		return action;
	}
	
	void CreateStateDomain (Topology topology) {
		stateSize = topology.size()*(topology.size()+1)*2;
		List<City> city = topology.cities();
		
		for (City  c1 : city){
			for(City c2 : city ) {
				states.add(new State (c1,c2,true));
			}
			states.add(new State (c1 , c1 , false));
			
		}
	}
	
	void CreateRewardTable(Topology topology ,TaskDistribution td) {
		R = new double[stateSize][nbAction];
		for (int i = 0; i < stateSize; i++) {
			for (int j = 0; j < nbAction; j++) {
				R[i][j]=0;
			}
		}
		List<City> cities = topology.cities();
		for(City c : cities){
			for(City neig : c.neighbors()){
				R[(new State(c , c , false )).toInt()][actionMoveTo(neig)] =  - c.distanceTo(neig) * costByKm;
				
			}
			for (City c2 : cities) {
				int pos = (new State(c , c2 , true )).toInt() ;
				R[pos][actionTakeTask] = td.reward(c, c2) - c.distanceTo(c2) * costByKm ;
				for(City neig : c.neighbors()){
					R[pos][actionMoveTo(neig)] =  - c.distanceTo(neig) * costByKm;
					
				}
			}
		}
	}
	
	private void CreateProbabilityTable (Topology topology , TaskDistribution td){
		T = new double[stateSize][nbAction][stateSize];
		for (int i = 0; i < stateSize; i++) {
			for (int j = 0; j < nbAction; j++) {
				for (int k = 0; k < stateSize; k++) {
					T[i][j][k] = 0;
					T[i][j][k] = 0;
				}
			}
			
		}
		List<City> cities = topology.cities();
		
		for(City c1 : cities) {
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
				
				
				T[(new State(c1, c2 , true)).toInt()]
						[actionTakeTask]
						[(new State(c2, c2 , false)).toInt()] = probaCount;
			
				//dont take task when task available
				for(City moveTo : c1.neighbors()){
					probaCount = 1 ;
					for(City newTask : cities){
						double cproba = td.probability(moveTo, newTask);
						probaCount -= cproba;
						T[(new State(c1, c2 , true)).toInt()]
								[actionMoveTo(moveTo)]
								[(new State(moveTo, newTask , true)).toInt()] = cproba;
					}
					T[(new State(c1, c2 , true)).toInt()]
							[actionMoveTo(moveTo)]
							[(new State(moveTo, moveTo , false)).toInt()] = probaCount;
				}
				
				
				
				
			}
			
			//dont take task when task not available
			for(City moveTo : c1.neighbors()){
				double probaCount = 1 ;
				for(City newTask : cities){
					double cproba = td.probability(moveTo, newTask);
					probaCount -= cproba;
					T[(new State(c1, c1 , false)).toInt()]
							[actionMoveTo(moveTo)]
							[(new State(moveTo, newTask , true)).toInt()] = cproba;
				}
				T[(new State(c1, c1 , false)).toInt()]
						[actionMoveTo(moveTo)]
						[(new State(moveTo, moveTo , false)).toInt()] = probaCount;
			}
		}
	}
	
	private void CreateIndicatingVector() {
		int noDiff = 0 ;
		V = new int[stateSize];
		double Qmax[] = new double[stateSize] ;
		for (int i = 0; i < stateSize; i++) {
			Qmax[i] = 0 ;
		}
		//TODO delete iter
		int iter = 0 ;
		while ( noDiff < stateSize && iter < 100){
			iter++;
			System.out.println(iter);
			noDiff=0;
			for(State s : states){
				
				int coord = s.toInt();
				
				List<City> neig = s.currentCityId.neighbors();
				
				int Qsize = neig.size() ;
				double Q[]= new double[Qsize];
				int action[]= new int[Qsize];
				
				
				//TODO Choose good method
				
				//////////////////method 1
				//list action move neighbor city
				int i = 0 ;
				for(City n : neig){
					int a = actionMoveTo(n);
					action[i] = a ;
					
					Q[i]= R[coord][a];
					for (State s2 : states) {
						Q[i] += pPickup* T[coord][a][s2.toInt()]*Qmax[coord];
					}
					i++;
				}
				
				//Find best choice between neighbors
				int bestAction = action[0] ; 
				double Qbest = Q[0] ; 
				for ( i = 0; i < Qsize; i++) {
					if (Q[i] > Qbest) {
						Qbest = Q[i] ;
						bestAction = action[i];
					}
				}
				
				//Compare to take task if possible
				if(s.canTakeTask){
					double QTakeTask= R[coord][actionTakeTask];
					for (State s2 : states) {
						QTakeTask += pPickup* T[coord][actionTakeTask][s2.toInt()]*Qmax[coord];
					}
					if (QTakeTask > Qbest){
						Qbest = QTakeTask ;
						bestAction = actionTakeTask;
					}
				}
				///////////////////////
				
				///////////////////// method 2
				/*Q = new double[nbAction];
				for (int j = 0; j < nbAction ; j++) {
					Q[j]= R[coord][j];
					for (State s2 : states) {
						Q[j] += pPickup* T[coord][j][s2.toInt()]*Qmax[coord];
					}
				}
				
				 bestAction = 0 ; 
				 Qbest = Q[0] ;
				for (int j = 0; j < nbAction ; j++) {
					if (Q[j] > Qbest) {
						Qbest = Q[j] ;
						bestAction = j;
					}
				}*/
				////////////////
				
				V[coord] = bestAction ;
				if(Qmax[coord] ==Qbest){
					noDiff++;
				}
				Qmax[coord] = Qbest;
			}
			
		}
	}
	
	private class State {
		
		public City currentCityId;
		public City toCityId ;
		public Boolean canTakeTask;
		
		public State (City ccid , City tcid , Boolean ctt  ) {
			currentCityId = ccid ;
			toCityId = tcid ;
			canTakeTask = ctt ;
		}
		
		public int toInt(){
			if(canTakeTask ) {
				return currentCityId.id *(nbCity+1)+toCityId.id;
			} else {
				return currentCityId.id *(nbCity+1)+nbCity;
			}
				
			
		}
	}
	private int actionMoveTo(City c) {
		return  c.id + 1 ;
	}
	
	private City cityFromAction ( int a ) {
		return listCity.get(a-1) ;
	}
}


