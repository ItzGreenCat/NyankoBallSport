package dev.onyxcat.processor;

import dev.onyxcat.session.Session;

import javax.swing.*;
import java.util.List;

public class RunningProcessor {
    public void processRun(RunningArguments args){
        if(Session.uid == -1) {
            JOptionPane.showMessageDialog(null, "你还未登录！");
            return;
        }
        PolicyArgument policy = null;
        try{
            policy = PolicyProcessor.getPolicy();
        } catch (Exception e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"无法获取Policy");
        }
        if(policy != null){
            List<Point> points = null;
            try{
               points  = PointProcessor.getPoints();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,"无法获取Point");
            }
            if(points != null) {
                System.out.println(String.format("[runningProcessor] 获取到%d个点位", points.size()));
            }

        }
    }
}
